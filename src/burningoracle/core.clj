(ns burningoracle.core
  (:require [clojure.string :as str]
            [burningoracle.dice :as dice]
            [clojure.java.io :as io])
  (:use [irclj.core]))

(declare oracle)

(def phrasebook-url (io/resource "phrasebook"))

(def canned-phrases
  (atom
   (read-string (slurp phrasebook-url))))

(defonce phrasebook-agent (agent nil))

(defn save-phrasebook! []
  (send phrasebook-agent (fn [_] (spit phrasebook-url (prn-str @canned-phrases)))))

(defn handle-canned
  [{:keys [pieces]}]
  (get @canned-phrases (.toLowerCase (first pieces))))

(def priviledged-users #{"brehaut" "Zelbinian"})

(defn learn-phrase [{:keys [nick pieces message]}]
  (prn nick pieces message)
  (when (contains? priviledged-users nick)
    (cond (= "forget" (first pieces)) (do
                                        (prn "forgetting")
                                        (swap! canned-phrases
                                               dissoc (second pieces))
                                        (save-phrasebook!)
                                        (str (second pieces) "? nope, never heard of it."))
          (= "is" (second pieces)) (do
                                     (when-let [[_ response] (re-matches #"^\S+\s+is\s+(.+)$"
                                                                         message)]
                                       (prn "learning"  response)
                                       (swap! canned-phrases
                                              assoc
                                              (first pieces) response)
                                       (save-phrasebook!)
                                       "sure thing boss.")))))

(defn nick-address [s]
  "returns a string for a nick or nil"
  (let [s   (.trim s)
        len (.length s)
        last-index (dec len)]
    (when (and (> len 0)
               (= \: (.charAt s last-index)))
      (.substring s 0 last-index))))

(defn addressed-command
  "an addressed-command only fires if the first piece is '_botnick_:'"
  [f]
  (fn [{:keys [channel pieces irc message] :as all}]
    (if (not= (.charAt channel 0) \#)
      (f all)
      (when-let [addr-nick (nick-address (first pieces))]
        (when (= (:name (dosync @irc)) addr-nick)
          (f (assoc all :pieces (rest pieces)
                    :message (-> message
                                 (.substring (.length (first pieces)))
                                 (.trim)))))))))

(defn first-of [fs]
  (let [fs (apply list fs)] ; we dont want to process the message with
                            ; more functions than necessary, and if a
                            ; vector is passed in we get a chunked seq
    (fn [{:keys [irc channel] :as all}]
      (when-let [response (first (keep #(% all) fs))]
        (when (string? response) (send-message irc channel response))))))


(defn sandwich
  "makes smart arse comments about sandwiches"
  [{:keys [message]}]
  (cond (re-find #"sudo\s+(sandwich|sammich)" message) "one sandwich coming right up"
        (re-find #"sandwich|sammich" message) "make your own damn sandwich"))

(def simple-responder (first-of [(addressed-command learn-phrase)
                                 handle-canned
                                 dice/handle-roll
                                 dice/handle-explode
                                 sandwich]))


(defn onmes [{:keys [message] :as all}]
  (let [pieces (map #(.toLowerCase %) (.split message " "))]        
    (#'simple-responder (assoc all :pieces pieces))))


(defonce oracle (create-irc {:name "burningbot"
                             :username "burningbot"
                             :server "irc.synirc.net"
                             :fnmap {:on-message #'onmes
                                     :on-connect (fn [_] (identify oracle))}}))

(defn start-bot []
  (connect oracle
           :channels ["#BurningWheel" "#burningbot"]))
