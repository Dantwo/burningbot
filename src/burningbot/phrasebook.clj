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
            [burningbot.settings :as settings]))

(def phrasebook-url "resources/phrasebook")

(def canned-phrases
  (ref
   (read-string (slurp phrasebook-url))))

(defonce phrasebook-agent (agent nil))

(defn save-phrasebook []
  (send phrasebook-agent (fn [_] (spit phrasebook-url (prn-str @canned-phrases)))))

(defn forget
  [key]
  (dosync
   (alter canned-phrases
           dissoc key)
   (save-phrasebook)))

(defn learn
  [key message]
  (when-let [[_ response] (re-matches #"^\S+\s+is\s+(.+)$"
                                      message)]
    (dosync
     (alter canned-phrases assoc key response)
     (save-phrasebook))))

(defn handle-learn-phrase [{:keys [nick pieces message]}]
  (prn nick pieces message)
  (when (contains? (settings/read-setting [:phrasebook :priviledged-users] #{}) (.toLowerCase) nick)
    (cond (= "forget" (first pieces)) (let [key (second pieces)]
                                        (forget key)
                                        (str key "? nope, never heard of it."))
          (= "is" (second pieces)) (do
                                     (learn (first pieces) message)
                                     "sure thing boss."))))

(defn handle-canned
  [{:keys [message]}]
  (get @canned-phrases (.toLowerCase (.trim message))))
