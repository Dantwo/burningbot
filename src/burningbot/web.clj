(ns burningbot.web
  "burningbot.web is the top level namespace for all the http listening logic."
  (:require [burningbot.settings :as settings]
            [burningbot.web.rpc :as rpc]
            [burningbot.logging :as logging])
  (:use [clojure.java.io :only [resource input-stream]]
        [net.cgrand.moustache :only [app delegate]]
        [ring.util.response :only [response content-type]]
        [ring.adapter.jetty :only [run-jetty]]))

(defn accept-ping-for-url [^java.net.URL url]
  (prn "ping url" url)
  (contains? (settings/read-setting [:rpc :weblog-updates :domains] #{})
             (.getHost url)))

(defn request-is-ajax?
  [req]
  (= (get-in req [:headers "x-requested-with"])
     "XMLHttpRequest"))

(defn log-for-day
  [req channel date]
  (if (request-is-ajax? req)
    (-> (logging/log-file channel date)
        response
        (content-type "text/plain; charset=utf8"))
    (response (input-stream (resource "logview.html")))))

(defn web-app
  "returns a new moustache web app"
  [{:keys [on-ping]}]
  (app ["rpc"] (rpc/new-weblogs-update-endpoint
                accept-ping-for-url
                on-ping)
       ["logs" [channel #"[a-z]+"] [date #"\d{4}-\d\d?-\d\d?"]] (delegate log-for-day channel date)
       [] ["Burningbot"]))

(defn webserver
  "returns a jetty instance that listens to a specified port. takes a map of delefate functiosn
   for particular events and queries such as rpc events and validation."
  [port callbacks]
  (run-jetty (web-app callbacks)
             {:port port
              :join? false}))
