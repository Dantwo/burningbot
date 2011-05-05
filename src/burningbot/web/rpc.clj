(ns burningbot.web.rpc
  "xml-rpc interfaces endpoints supported by burningbot"
  (:require [necessary-evil.core :as xmlrpc]))

(defn ping-response
  [error? message]
  {:flerror error?
   :message message
   :legal "IANAL"})

(def successful (ping-response false "Thanks for the ping."))

(defn new-weblogs-update-endpoint
  "returns a new weblogUpdates endpoint. see http://www.weblogs.com/api.html"
  [allowed-site? on-ping]
  (xmlrpc/end-point
   {:weblogUpdates.extendedPing
    (fn [name url updated feed ^String tags]
      (if (allowed-site? (java.net.URL. url))
        (do (on-ping {:site-name name                
                      :post      (java.net.URL. updated)
                      :tags      (seq (.split tags "\\|"))})
            successful)
        (ping-response true "that site is now allowed to ping the burningbot")))}))
