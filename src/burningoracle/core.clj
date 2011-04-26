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
  [nick [cmd & r] message]
  (get @canned-phrases (.toLowerCase cmd)))

(def priviledged-users #{"brehaut" "Zelbinian"})

(defn learn-phrase [nick pieces message]
  (when (contains? priviledged-users nick)
    (cond (= "forget" (first pieces)) (do
                                        (swap! canned-phrases
                                               dissoc (second pieces))
                                        (save-phrasebook!)
                                        (str (second pieces) "? nope, never heard of it."))
          (= "is" (second pieces)) (do
                                     (when-let [[_ response] (re-matches #"^[^:]*:\s*\S+\s+is\s+(.+)$"
                                                                         message)]
                                       (swap! canned-phrases
                                              assoc
                                              (first pieces) response)
                                       (save-phrasebook!)
                                       "sure thing boss.")))))


(defn addressed-command [f]
  (fn [nick [address? & pieces] message]
    (let [len (.length address?)
          last-index (dec (.length address?))]
      (when (and (> len 0)
                 (= \: (.charAt address? last-index))
                 (= (:name (dosync @oracle))
                    (.substring address? 0 last-index)))
        (f nick pieces message)))))

(defn first-of [fs]
  (fn [irc channel nick pieces message]
    (when-let [response (first (keep #(% nick pieces message) fs))]
      (send-message irc channel response))))

(def simple-responder (first-of [(addressed-command learn-phrase)
                                 handle-canned
                                 dice/handle-roll
                                 dice/handle-explode]))


(defn onmes [{:keys [nick channel message irc] :as all}]
  (prn channel)
  (let [pieces (map #(.toLowerCase %) (.split message " "))]
    (#'simple-responder irc channel nick pieces message)))


(defonce oracle (create-irc {:name "burningbot"
                             :username "burningbot"
                             :server "irc.synirc.net"
                             :fnmap {:on-message #'onmes
                                     :on-connect (fn [_] (identify oracle))}}))

(defn start-bot []
  (connect oracle
           :channels ["#BurningWheel"]))
