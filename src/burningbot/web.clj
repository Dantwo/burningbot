(ns burningbot.web
  "burningbot.web is the top level namespace for all the http listening logic."
  (:require [clj-time.core :as time]
            [burningbot.settings :as settings]
            [burningbot.web.rpc :as rpc]
            [burningbot.web.views :as views]
            [burningbot.logging :as logging])
  (:use [clojure.java.io :only [resource input-stream]]
        [net.cgrand.moustache :only [app delegate]]
        [ring.util.response :only [response content-type]]
        [ring.adapter.jetty :only [run-jetty]]
        [clj-time.format :only [unparse]]
        [burningbot.web.utils :only [html-response
                                     json-response
                                     request-is-ajax?]]))



(defn accept-ping-for-url [^java.net.URL url]
  (prn "ping url" url)
  (contains? (settings/read-setting [:rpc :weblog-updates :domains] #{})
             (.getHost url)))

;; logs and api

(defn log-date [s]
  (when-let [[_ & r] (re-matches #"(\d{4})-(\d{1,2})-(\d{1,2})" s)]
    (apply time/date-time (map #(Integer/parseInt %) r))))

(defn time-to-json-string
  [datetime]
  (unparse logging/time-format datetime))

(defn log-for-day
  [req channel date]
  (-> (logging/log-file channel date)
      response
      (content-type "text/plain; charset=utf8")))

(defn log-viewer
  [req channel date]
  (when (logging/log-exists? channel date)
    (-> (response (views/main-template (str "Log for " channel " " date) views/loading))
        (content-type "text/html; charset=utf8"))))

(defn- mark-dates-to-string)

(defn log-meta [req channel date]
  (when-let [data (logging/log-metadata channel date)]
    (-> data
        (update-in [:marks] #(map (fn [{:keys [start end] :as mark}]
                                    (assoc mark
                                      :start (time-to-json-string start)
                                      :end   (time-to-json-string end)))
                                  %))
        (assoc :text (str "/api/logs/" channel "/" (logging/log-filename date) "/text"))
        json-response )))

;; general

(defn static-view
  [& template-args]
  (fn [req] (html-response (apply views/main-template template-args ))))



(def web-api
  (app
   ["logs" [channel #"[a-z]+"] [date log-date] &]
   (app
    ["meta"] (delegate log-meta channel date)
    ["text"] (delegate log-for-day channel date))))

(defn web-app
  "returns a new moustache web app"
  [{:keys [on-ping]}]
  (app ["rpc"] (rpc/new-weblogs-update-endpoint
                accept-ping-for-url
                on-ping)
       ["api" &] web-api 
       ["logs" [channel #"[a-z]+"] [date log-date]] (delegate log-viewer channel date)
       ["colophon"] (static-view "Colophon" views/colophon)
       [] (static-view "burningbot" views/home)))

(defn webserver
  "returns a jetty instance that listens to a specified port. takes a map of delefate functiosn
   for particular events and queries such as rpc events and validation."
  [port callbacks]
  (run-jetty (web-app callbacks)
             {:port port
              :join? false}))
