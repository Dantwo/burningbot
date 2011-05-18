(ns burningbot.web
  "burningbot.web is the top level namespace for all the http listening logic."
  (:require [burningbot.settings :as settings]
            [burningbot.web.rpc :as rpc]
            [burningbot.web.views :as views]
            [burningbot.logging :as logging])
  (:use [clojure.java.io :only [resource input-stream]]
        [net.cgrand.moustache :only [app delegate]]
        [ring.util.response :only [response content-type]]
        [ring.adapter.jetty :only [run-jetty]]
        [burningbot.web.utils :only [html-response
                                     json-response
                                     request-is-ajax?]]))



(defn accept-ping-for-url [^java.net.URL url]
  (prn "ping url" url)
  (contains? (settings/read-setting [:rpc :weblog-updates :domains] #{})
             (.getHost url)))


(defn log-for-day
  [req channel date]
  (if (request-is-ajax? req)
    (-> (logging/log-file channel date)
        response
        (content-type "text/plain; charset=utf8"))
    (-> (response (views/main-template (str "Log for " channel " " date) views/loading))
        (content-type "text/html; charset=utf8"))))

(defn static-view
  [& template-args]
  (fn [req] (html-response (apply views/main-template template-args ))))

(defn log-meta [req channel date]
  (json-response {}))

(def web-api
  (app
   ["logs" [channel #"[a-z]+"] [date #"\d{4}-\d\d?-\d\d?"] &]
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
       ["logs" [channel #"[a-z]+"] [date #"\d{4}-\d\d?-\d\d?"]] (delegate log-for-day channel date)
       ["colophon"] (static-view "Colophon" views/colophon)
       [] (static-view "burningbot" views/home)))

(defn webserver
  "returns a jetty instance that listens to a specified port. takes a map of delefate functiosn
   for particular events and queries such as rpc events and validation."
  [port callbacks]
  (run-jetty (web-app callbacks)
             {:port port
              :join? false}))
