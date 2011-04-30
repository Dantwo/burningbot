(ns burningbot.dice
  (:require [clojure.string :as str]))

;; random functions

(defn ^:private die [] (int (Math/ceil (* 6 (Math/random)))))

(def ^:private die-seq (partial repeatedly die))

;; done with random. everything else should consume die-seq

(defn count-success [break n] (>= n break))

(defn six? [n] (= n 6))

(def count-where (comp count filter))

(defn- explode-1
  [[dice die-seq]]
  (let [n (count-where six? dice)]
    (when (> n 0) (split-at n die-seq))))

(defn explode
  "takes a seq of dice that have been rolled, and a die-seq and explodes all the dice"
  [rolled die-seq]
  (mapcat first (take-while (complement nil?)
                            (iterate explode-1 [rolled die-seq]))))

(defn roll
  "roll takes a dice and returns a map of :rolled and :result"
  ([dice] (roll dice (die-seq)))
  ([{:keys [shade exponent exploding?] :as dice} die-seq]
     (let [[rolled die-seq] (split-at (Math/min 20 exponent) die-seq)
           rolled ((if exploding? #(explode % die-seq) identity) rolled)
           successes (count-where (case shade
                                        "b" (partial count-success 4)
                                        "g" (partial count-success 3)
                                        "w" (partial count-success 2))
                                  rolled)]
       {:dice dice
        :rolled rolled
        :successes successes})))

(def die-re #"([bgw])(\d+)(\*?)")
(def ob-re  #"(ob)(\d+)")
(def vs-re  (re-pattern (str "(" die-re "|" ob-re ")")))

(defn unpack-dice
  "unpack-dice takes a string and returns map or nil. If the expression is not a valid dice
   expression nil is returned, otherwise the map will contain :shade, :exponent, and :exploding?"
  [exp]
  (when-let [[_ shade n exploding] (re-matches die-re exp)]
    {:shade shade :exponent (Integer/parseInt n) :exploding? (= exploding "*")}))

(defn unpack-obstacle
  "unpack obstacle takes a string containing an obstacle number (ob\\d+) or another dice roll and
   produces either a map with a :success key. it may or may not also contain dice info as the result
   of a roll (for vs tests)"
  [exp]
  (when-let [[_ _ shade exponent exploding _ obstacle] (re-matches vs-re exp)]
    (if obstacle
      {:successes (Integer/parseInt obstacle)}
      (roll {:shade shade
             :exponent (Integer/parseInt exponent)
             :exploding? (= exploding "*")}))))

(defn- result-to-str
  [{{:keys [shade exponent exploding?]} :dice
    :keys [successes rolled]}]
  (if rolled
    (str shade exponent (when exploding? "*") " → (" (str/join "," rolled) ") " successes " successes")
    (str "ob" successes)))

(defn pack-result
  [nick result obstacle]
  (str nick " rolled " (result-to-str result)
       (when obstacle
         (let [attacker (:successes result)
               defender (:successes obstacle)] (str " vs " (result-to-str obstacle) " ⇒ "
                                                    (if (< attacker defender)
                                                      "failed by "
                                                      "succeeded by ")
                                                    (Math/abs (- attacker defender)))))))

(def users-last-explodables (ref {}))

(defn- save-roll
  [nick dice]
  (dosync (alter users-last-explodables assoc nick dice)))

(defn handle-roll
  [{:keys [nick message]
    [exp1 vs? exp2] :pieces}]
  (when-let [dice (unpack-dice exp1)]
    (let [result   (roll dice)
          vs?      (= "vs" vs?)
          obstacle (when vs? (unpack-obstacle exp2))]
      (save-roll nick (assoc result :obstacle obstacle))
      (pack-result nick result obstacle))))

(defn handle-explode
  [{:keys [nick pieces message]}]
  (when (= "boom" (first pieces)) 
    (when-let [{:keys [dice rolled successes obstacle]} (get @users-last-explodables nick nil)]
      (dosync (alter users-last-explodables dissoc nick))
      (when (not (:exploded? dice))
        (let [to-explode (count-where six? rolled)]
          (when (> to-explode 0)
            (let [new-result (roll {:exploding? true
                                    :shade (:shade dice)
                                    :exponent to-explode})
                  {new-successes :successes new-rolled :rolled } new-result
                  
                  joined {:successes (+ successes new-successes)
                          :rolled    (concat rolled new-rolled)
                          :dice      (assoc dice :exploding? true)}]

              (pack-result nick joined obstacle))))))))
