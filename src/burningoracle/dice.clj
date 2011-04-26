(ns burningoracle.dice
  (:require [clojure.string :as str]))


(defn die [] (int (Math/ceil (* 6 (Math/random)))))

(defn count-success [break n] (when (>= n break) 1))

(defn explode
  ([s] (explode s ()))
  ([s acc]
     (let [newdice (keep #(when (= 6 %) (die)) s)]
       (if (seq newdice) (recur newdice (concat s acc))
           (concat s acc)))))

(defn roll [shade n explodes?]
  (let [dice ((if explodes? explode identity) (repeatedly (Math/min n 20) die))
        successes (apply + (keep (case shade
                                       "b" (partial count-success 4)
                                       "g" (partial count-success 3)
                                       "w" (partial count-success 2))
                                 dice))]
    [dice successes]))

(def die-re #"^([bgw])(\d+)(\*?)$")

(def users-last-explodables (atom {}))

(defn handle-roll [nick [cmd & _] message]
  (when-let [[_ shade n exploding :as all] (re-matches die-re cmd)]
    (let [[dice successes] (roll shade (Integer/parseInt n) (= "*" exploding))]
      (swap! users-last-explodables
             assoc
             nick (str shade (count (filter #(= % 6) dice)) "*"))
      (str nick " rolled " cmd "→"
         " [" (str/join "," dice) "] "
         successes " successes"))))

(defn handle-explode
  [nick [cmd & _] message]
  (when (= "boom" cmd) 
    (when-let [roll (get @users-last-explodables nick nil)]
      (handle-roll nick [roll] nil))))
