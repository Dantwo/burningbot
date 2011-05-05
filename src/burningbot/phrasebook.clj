(ns burningbot.phrasebook
  "This is one of the oldest modules in burningbot. it stores messages by key and retrieves them again.
   it is desperately in need of being rewritten. This will probably occur once some sql approach has
   been decided on.

   features for the future:
     * should hook into a general keyword lookup feature so that key finding logic is generalised
     * provide tools for multiple possible answers
     * responses with 'key _is_ response' and optionally 'key _are_ response' responses (similar to clojurebot)
     * allow keys up to three words"
  
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [burningbot.settings :as settings]
            [burningbot.db :as db]))

(defn- learn-fact!
  [channel key message nick]
  (when-let [[_ response] (re-matches #"^\S+\s+is\s+(.+)$"
                                      message)]
    (db/insert-fact! channel key response nick)))

(defn handle-learn-phrase [{:keys [channel nick pieces message]}]
  (prn nick pieces message)
  (when (contains? (settings/read-setting [:phrasebook :channels] #{}) (.toLowerCase channel))
    (cond (= "forget" (first pieces)) (let [key (second pieces)]
                                        (db/delete-fact! channel key)
                                        (str key "? nope, never heard of it."))
          (= "is" (second pieces)) (do
                                     (learn-fact! channel (first pieces) message nick)
                                     "sure thing boss.")
          (= "definitions?")       (str/join " " (db/query-fact-names channel)))))

(defn handle-canned
  [{:keys [channel message]}]
  (when-let [response (first (db/query-fact channel (.toLowerCase (.trim message))))]
    (str message " is " (:value response))))
