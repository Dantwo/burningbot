(ns burningbot.core
  "This module ties together all the components of burningbot and hooks it into an irclj ircbot"
  (:require [clojure.string :as str]         
            [burningbot.dice :as dice]
            [burningbot.invitation :as invitation]
            [burningbot.logging :as logging]
            [burningbot.scraper :as scraper]
            [burningbot.settings :as settings]
            [burningbot.phrasebook :as phrasebook])
  (:use [irclj.core]
        [burningbot.settings :only [settings]]
        [burningbot.plumbing :only [guard
                                    send-response-as-message
                                    first-of
                                    addressed-command
                                    ignore-address]]
        [clojure.pprint :only [pprint]]))

(defn sender-is-bot?
  "tests the incoming messages nick to see if it is in the ignore-set in settings or ends with 'bot';
   either causes the predicate to return true."
  [{nick :nick}]
  (or (.endsWith nick "bot")
      ((settings/read-setting :ignore-set #{}) nick)))

(defn sandwich
  "makes smart arse comments about sandwiches"
  [{:keys [message]}]
  (cond (re-find #"sudo\s+(sandwich|sammich)" message) "one sandwich coming right up"
        (re-find #"sandwich|sammich" message) "make your own damn sandwich"))


(def ^{:doc "message-responder is the main pipeline of handlers for on-message calls.
             various handlers are combined with burningbot.plumbing; see that lib for details.

             currently this processing is sequential but in future may be sent off to an agent
             some other background thread such as a future or something with reactors."}
  message-responder
  (guard [(complement sender-is-bot?)
          invitation/authorized-for-channel?]
         (send-response-as-message
          (first-of [(addressed-command
                      (first-of [phrasebook/handle-learn-phrase
                                 scraper/handle-tags
                                 ;;invitation/handle-invite
                                 sandwich]))
                     (ignore-address
                      (first-of [phrasebook/handle-canned
                                 dice/handle-roll
                                 dice/handle-explode]))
                     scraper/handle-scrape]))))

;; irclj nuts and bolts

(defn on-message [{:keys [message] :as all}]
  "handles incoming messages"
  (let [pieces (map #(.toLowerCase %) (.split message " "))]        
    (#'message-responder (assoc all :pieces pieces))
    (logging/handle-logging all)))

(defn on-join
  "handles incoming join notification"
  [all]
  (invitation/handle-join all))

(defonce bot (create-irc (merge (settings/read-setting :irclj)
                                {:fnmap {:on-message #'on-message
                                         ;;:on-join #'on-join
                                         :on-connect (fn [_] (identify bot))}})))

(defn start-bot
  []
  (connect bot :channels (settings/read-setting :starting-channels)))

