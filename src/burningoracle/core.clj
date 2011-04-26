(ns burningoracle.core
  (:require '[clojure.string :as str])
  (:use '[irclj.core]))

(defn die [] (int (Math/ceil (* 6 (Math/random)))))

(defn count-success [break n] (when (>= n break) 1))

(defn roll [exp]
  (let [shade (case (.substring exp 0 1)
                    "b" (partial count-success 4)
                    "g" (partial count-success 3)
                    "w" (partial count-success 2)
                    (constantly true))
        n (Integer/parseInt (.substring exp 1))
        dice (repeatedly n die)
        successes (apply + (keep shade dice))]
    (prn dice successes)
    (str exp " [" (str/join "," dice) "] " successes " successes")))


(def canned-phrases
  {"ugt?" "UGT is http://www.total-knowledge.com/~ilya/mips/ugt.html"
  })

(def priviledged-users ["brehaut"])

(defn onmes [{:keys [nick channel message irc] :as all}]
  (prn channel)
  (let [[cmd & r] (.split message " ")
        cmd (.toLowerCase cmd)]
    (cond 
     (contains? canned-phrases cmd) (send-message irc channel (canned-phrases cmd))
     
     (re-matches #"^[bwg]\d+$" cmd) (send-message irc channel (roll cmd)))))


(def oracle (create-irc {:name "burningoracle"
                               :username "burningoracle"
                               :server "irc.synirc.net"
                               :fnmap {:on-message #'onmes}}))

(def bot (connect oracle
		  :channels ["#BurningWheel"]))
