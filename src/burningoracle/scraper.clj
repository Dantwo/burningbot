(ns burningoracle.scraper
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as str]
            [irclj.core :as irclj]))

(def ^{:dynamic true} *max-redirects* 3)

(defn follow-url*
  "follows an http url that redirects. Returns the final url if it resolves
   to a legit page (no redirect loops etc)."
  ([url] (follow-url* url #{}))
  ([url urls-seen]
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
                      :else nil))
              (finally (.disconnect conn)))))))

(defn follow-url
  [& a]
  (let [f (future (apply follow-url* a))
        timeout (future (do (Thread/sleep (* 30 1000))
                            (when (not (future-done? f))
                              (future-cancel f))))]
    (try @f (catch Exception e nil))))

(defn scrape-bw-forum
  [doc]
  {:title (apply str (map html/text (html/select doc [:.threadtitle])))
   :tags (map html/text (html/select doc [:#thread_tags_list :.commalist :a]))})

(defn bw-forum-format
  [info]
  (let [title (:title info)]
    (when (seq title)
      (str "'" title "'" (when-let [tags (-> info :tags seq)]
                           (str " tagged as " (str/join ", " tags)))))))

(def special-domains {"www.burningwheel.org" ::burning-wheel
                      "burningwheel.org"     ::burning-wheel
                      "www.burningwheel.com" ::burning-wheel
                      "burningwheel.com"     ::burning-wheel})

(defmulti scrape-page (fn [url irc channel] (special-domains (.getHost url))))

(defmethod scrape-page ::burning-wheel
  [url irc channel]
  (let [doc (html/html-resource url)
        response (-> doc scrape-bw-forum bw-forum-format)]
    (when response
      (irclj/send-message irc channel response))))

(def scraper (agent nil)) ; acts as a queue of urls to scrape

(defn queue-scrape
  [url irc channel]
  (future (let [resolved-url (follow-url url)]
                                        ; broadcast final url if it differs from the posted url

            (when (and resolved-url
                       (not (.sameFile url resolved-url)))
              (irclj/send-message irc channel (str "⇒ " resolved-url)))
            
                                        ; display domain specific behavior
            (when resolved-url (scrape-page resolved-url irc channel)))))


(def weburl-re #"(http://[^/\s]*(/\S*)?)")

(defn handle-scrape
  [{:keys [message irc channel]}]
  (when-let [[url] (re-find weburl-re message)]
    (let [url    (java.net.URL. url)]
      (prn ">>" url)
      (when (#{"http" "https"} (.getProtocol url))
        (queue-scrape url irc channel)))))
