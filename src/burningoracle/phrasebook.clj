(ns burningoracle.phrasebook
  (:require [clojure.java.io :as io]))


(def phrasebook-url "resources/phrasebook")

(def canned-phrases
  (atom
   (read-string (slurp phrasebook-url))))

(defonce phrasebook-agent (agent nil))

(defn save-phrasebook! []
  (send phrasebook-agent (fn [_] (spit phrasebook-url (prn-str @canned-phrases)))))

(defn forget!
  [key]
  (swap! canned-phrases
         dissoc key)
  (save-phrasebook!))

(defn learn!
  [key message]
  (when-let [[_ response] (re-matches #"^\S+\s+is\s+(.+)$"
                                      message)]
    (swap! canned-phrases assoc key response)
    (save-phrasebook!)))

(def priviledged-users #{"brehaut" "Zelbinian" "cathexis" "oliof"})

(defn handle-learn-phrase [{:keys [nick pieces message]}]
  (prn nick pieces message)
  (when (contains? priviledged-users nick)
    (cond (= "forget" (first pieces)) (let [key (second pieces)]
                                        (forget! key)
                                        (str key "? nope, never heard of it."))
          (= "is" (second pieces)) (do
                                     (learn! (first pieces) message)
                                     "sure thing boss."))))

(defn handle-canned
  [{:keys [pieces]}]
  (get @canned-phrases (.toLowerCase (first pieces))))
