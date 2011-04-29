(ns burningbot.core
  (:require [clojure.string :as str]
            [burningbot.dice :as dice]
            [burningbot.scraper :as scraper]
            [burningbot.settings :as settings]
            [burningbot.phrasebook :as phrasebook])
  (:use [irclj.core]
        [burningbot.settings :only [settings]]))

(declare bot)

(defn nick-address [s]
  "returns a string for a nick or nil"
  (let [s   (.trim s)
        len (.length s)
        last-index (dec len)]
    (when (and (> len 0)
               (= \: (.charAt s last-index)))
      (.substring s 0 last-index))))

(defn strip-nick-address
  [first-piece nick message]
  (if (= nick (nick-address first-piece))
    (-> message
        (.substring (.length first-piece))
        (.trim))
    message))

(defn addressed-command
  "an addressed-command only fires if the first piece is '_botnick_:'"
  [f]
  (fn [{:keys [channel pieces irc message] :as all}]
    (let [botname (:name (dosync @irc))]
      (if (not= (.charAt channel 0) \#)
        (f all)
        (when-let [addr-nick (nick-address (first pieces))]
          (when (= botname addr-nick)
            (f (assoc all                 :pieces (rest pieces)
                 :message (strip-nick-address (first pieces)
                                                  botname
                                                  message)))))))))

(defn first-of [fs]
  (let [fs (apply list fs)] ; we dont want to process the message with
                            ; more functions than necessary, and if a
                            ; vector is passed in we get a chunked seq
    (fn [{:keys [irc channel] :as all}]
      (when-let [response (first (keep #(% all) fs))]
        (when (string? response) (send-message irc channel response))))))

(defn ignore-address
  [f]
  (fn [{:keys [message pieces irc] :as all}]
    (let [botname (:name (dosync @irc))
          new-message (strip-nick-address (first pieces) botname message)]
      (if (= message new-message)
        (f all)
        (f (assoc all :pieces (rest pieces)
                  :message new-message))))))

(defn sandwich
  "makes smart arse comments about sandwiches"
  [{:keys [message]}]
  (cond (re-find #"sudo\s+(sandwich|sammich)" message) "one sandwich coming right up"
        (re-find #"sandwich|sammich" message) "make your own damn sandwich"))

(def simple-responder (first-of [(addressed-command phrasebook/handle-learn-phrase)
                                 (ignore-address phrasebook/handle-canned)
                                 dice/handle-roll
                                 dice/handle-explode
                                 sandwich
                                 scraper/handle-scrape
                                 (addressed-command scraper/handle-tags)]))


(defn onmes [{:keys [message] :as all}]
  (let [pieces (map #(.toLowerCase %) (.split message " "))]        
    (#'simple-responder (assoc all :pieces pieces))))

(defonce bot (create-irc (merge (settings/read-setting :irclj)
                                {:fnmap {:on-message #'onmes
                                         :on-connect (fn [_] (identify bot))}})))

(defn start-bot
  []
  (connect bot :channels (settings/read-setting :starting-channels)))

