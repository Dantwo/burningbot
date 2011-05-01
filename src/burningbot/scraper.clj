(ns burningbot.scraper
  "the scraper finds urls in messages and follows them. If the url resolves in a domain that it is
   configured to scrape, it does so and provides additional information."
  
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as str]
            [irclj.core :as irclj]
            [clj-time.core :as time]
            [burningbot.settings :as settings])
  (:import [java.net URL]))

(def ^{:dynamic true} *max-redirects* 3)

(defonce ^{:doc "tagstore is a map of tag to url populated when pages are scrapped."}
  tagstore
  (ref {}))


(defonce ^{:private true
           :doc     "site rules is map of rules on a domain and path fragment basis. it is derived from
                     the [:scraping :sites] map in the bot settings."}
  site-rules
  (atom nil))

;; load settings and create site-rules

(defn- rebuild-rules
  [settings]
  (into {} (for [[domains config] settings
                 domain           (if (string? domains) [domains] domains)]
             [domain config])))

(defn- latest-rules!
  "returns the latest rules, updating them if needed."
  []
  (let [rules            @site-rules
        settings-updated (settings/last-updated)]
    (:rules (if (or (nil? rules)
                    (time/after? settings-updated (:last-updated rules)))
              (reset! site-rules {:last-updated settings-updated
                                  :rules        (rebuild-rules (settings/read-setting [:scraping :sites]))})
              rules))))

(defn rules-for-url
  "looks up the settings for the url"
  [^URL url]
  (let [rules   (latest-rules!)
        domain  (.getHost url)
        path    (.getPath url)
        by-path (rules domain)]
    (first (keep (fn [[prefix config]] (when (.startsWith path prefix) config))
                 by-path))))

;; the following code allows the bot to follow urls and provide
;; reasonable feedback to channel participants 

(defn follow-url*
  "follows an http url that redirects. Returns the final url if it resolves
   to a legit page (no redirect loops etc)."
  ([^URL url] (follow-url* url #{}))
  ([^URL url urls-seen]
     (when (and (> *max-redirects* (count urls-seen))
                (not (contains? urls-seen url)))
       (let [conn      (.openConnection url)
             urls-seen (conj urls-seen url)]
         (try (doto conn
                (.setInstanceFollowRedirects false)
                (.setRequestMethod "HEAD")
                (.connect))
              (let [code (.getResponseCode conn)]
                (cond (contains? #{301 302 303 307} code) (recur (-> conn
                                                                     (.getHeaderField "Location")
                                                                     (java.net.URL.))
                                                                 urls-seen)
                      (contains? #{200} code) url
                      :else (do (prn ">>>>>>>." code) nil)))
              (finally (.disconnect conn)))))))

(defn follow-url
  [& a]
  (let [f (future (apply follow-url* a))
        timeout (future (do (Thread/sleep (* 30 1000))
                            (when (not (future-done? f))
                              (future-cancel f))))]
    (try @f (catch Exception e nil))))

(defn normalize-href
  "pages such as we get from vbulletin might provide is us with relative urls
   for hrefs. we need to normalise them relative to the page we scraped."
  [^URL page-url href]
  (str (URL. page-url href)))


(defn format-scrape-result
  "This takes a map from perform-scraping and returns a string or nil."
  [info page-title]
  (let [title (:title info)
        tags  (:tags info)]

    (when (seq tags)
      (dosync (ref-set tagstore (into {} (map (fn [[k v]] [(str k "?") v])
                                       tags)))))
    
    (when (seq title)
      (str  "⇒  " page-title ": '" title "'"
            (when-let [tags (seq tags)]
              (str " tagged as " (str/join ", " (keys tags))))))))

(defmulti perform-scraping (fn [^URL u r d] (:system r)))

(defmethod perform-scraping :vbulletin3
  [^URL url rules doc]
  {:title (apply str (map html/text (html/select doc [:.threadtitle])))
   :tags (into {} (map (juxt html/text
                             (comp (partial normalize-href url) :href :attrs))
                       (html/select doc [:#thread_tags_list :.commalist :a])))})

(defmethod perform-scraping :wordpress
  [^URL url rules doc]
  (when-let [title (first (map html/text (html/select doc [:title])))]
    {:title (first (.split title (:title-sep rules)))}))

(defmethod perform-scraping :wikimedia
  [^URL url rules doc]
  (prn "scraping wikimedia")
  {:title (apply str (map html/text (html/select doc [:#firstHeading])))
   :tags  (keep (fn [{{:keys [title href]} :attrs :as node}]
                  (when (.startsWith title "Category:")
                    [(.toLowerCase (html/text node)) href]))
                (html/select doc [:.catlinks :a]))})

(defmethod perform-scraping :default [_ _ _] nil)



(defn scrape-page
  [^URL url irc channel]
  (prn url)
  (when-let [rules (rules-for-url url)]
    (prn url rules)
    (let [doc  (html/html-resource url)
          info (perform-scraping url rules doc)
          msg  (format-scrape-result info (:title rules))]
      (when msg
        (irclj/send-message irc channel msg)
        true))))


(def scraper (agent nil)) ; acts as a queue of urls to scrape

(defn queue-scrape
  "broadcasts a url if it differs from the posted url. Additionally it is aware of specific sites
   such as burningwheel forums. If we have a transformation for this particular page, that will
   always be displayed instead of the url even if there is an expansion."
  [^URL url irc channel]
  (future (when-let [resolved-url (follow-url url)]
            ;; broadcast final url if it differs from the posted url
            (prn "R"  resolved-url)
            (if-not (.sameFile url resolved-url)
              (or (scrape-page resolved-url irc channel)
                  (irclj/send-message irc channel (str "⇒ " resolved-url)))
              
                                        ; display domain specific behavior
              (scrape-page resolved-url irc channel)))))


(def weburl-re #"(http://[^/\s]*(/\S*)?)")

(defn handle-scrape
  [{:keys [message irc channel]}]
  (when-let [[url] (re-find weburl-re message)]
    (let [url (URL. url)]
      (when (#{"http" "https"} (.getProtocol url))
        (queue-scrape url irc channel)))))


(defn handle-tags
  "burningbot will record tags from the last link that provided them.

  by requesting the tag as a message burningbot will produce the url."
  [{:keys [message pieces irc channel]}]
  (when (= \? (last (first pieces)))
    (when-let [url (dosync (@tagstore (first pieces)))]
      (str "That tag is " url))))
