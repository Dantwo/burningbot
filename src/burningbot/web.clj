(ns burningbot.web
  "burningbot.web is the top level namespace for all the http listening logic."
  (:require [burningbot.settings :as settings]
            [burningbot.web.rpc :as rpc])
  (:use [net.cgrand.moustache :only [app]]
        [ring.adapter.jetty :only [run-jetty]]))

(defn accept-ping-for-url [^java.net.URL url]
  (contains? (settings/read-setting [:rpc :weblog-updates :domains] #{})
             (.getHost url)))

(defn web-app
  "returns a new moustache web app"
  [{:keys [on-ping]}]
  (app ["rpc"] (rpc/new-weblogs-update-endpoint
                accept-ping-for-url
                on-ping)
       [] ["Burningbot"]))

(defn webserver
  "returns a jetty instance that listens to a specified port. takes a map of delefate functiosn
   for particular events and queries such as rpc events and validation."
  [port callbacks]
  (run-jetty (web-app callbacks)
             {:port port
              :join? false}))
