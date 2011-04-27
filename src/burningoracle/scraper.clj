(ns burningoracle.scraper
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as str]
            [irclj.core :as irclj]))

(defn fetch-url [url]
  (html/html-resource (java.net.URL. url)))

(defn scrape-burning-wheel-forum
  [doc]
  {:title (apply str (map html/text (html/select doc [:.threadtitle])))
   :tags (map html/text (html/select doc [:#thread_tags_list :.commalist :a]))})

(defn bw-forum-format
  [info]
  (when-let [title (:title info)]
    (str "'" title "'" (when-let [tags (-> info :tags seq)]
                 (str " tagged as " (str/join ", " tags))))))

(def weburl-re #"(http://([^/\s]*)(/\S*)?)")

(def scraper (agent nil))
(defn queue-scrape [url irc channel formater]
  (send scraper (fn [_] (let [doc (fetch-url url)
                             response (formater doc)]
                          (when response (irclj/send-message irc channel response))))))

(defn handle-scrape
  [{:keys [message irc channel]}]
  (when-let [[_ url domain] (re-find weburl-re (.toLowerCase message))]
    (cond (#{"www.burningwheel.org"
             "burningwheel.org"
             "www.burningwheel.com"
             "burningwheel.com"} domain) (queue-scrape url irc channel
                                                       (comp bw-forum-format scrape-burning-wheel-forum)))))
