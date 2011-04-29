(ns burningbot.dice
  (:require [clojure.string :as str]))


(defn die [] (int (Math/ceil (* 6 (Math/random)))))

(defn count-success [break n] (>= n break))

(defn explode
  ([s] (explode s ()))
  ([s acc]
     (let [newdice (keep #(when (= 6 %) (die)) s)]
       (if (seq newdice) (recur newdice (concat acc s))
           (concat acc s)))))

(defn roll
  "roll takes a dice and returns a map of :rolled and :result"
  [{:keys [shade exponent exploding?] :as dice}]
  (let [rolled ((if exploding? explode identity) (repeatedly (Math/min exponent 20) die))
        successes (count (filter (case shade
                                       "b" (partial count-success 4)
                                       "g" (partial count-success 3)
                                       "w" (partial count-success 2))
                                 rolled))]
    {:dice dice
     :rolled rolled
     :successes successes}))

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
  [exp]
  ""
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
       (when obstacle (str " vs " (result-to-str obstacle)
                           " ⇒ " (Math/max 0 (- (:successes result)
                                                (:successes obstacle)))
                           " successes"))))

(def users-last-explodables (atom {}))

(defn- save-roll!
  [nick dice]
  (swap! users-last-explodables
         assoc nick dice))

(defn handle-roll [{:keys [nick message]
                    [exp1 vs? exp2] :pieces}]
  (when-let [dice (unpack-dice exp1)]
    (let [result   (roll dice)
          vs?      (= "vs" vs?)
          obstacle (when vs? (unpack-obstacle exp2))]
      (save-roll! nick (assoc result :obstacle obstacle))
      (pack-result nick result obstacle))))

(defn handle-explode
  [{:keys [nick pieces message]}]
  (when (= "boom" (first pieces)) 
    (when-let [{:keys [dice rolled successes obstacle]} (get @users-last-explodables nick nil)]
      (swap! users-last-explodables dissoc nick)
      (when (not (:exploded? dice))
        (let [to-explode (count (filter #(= % 6) rolled))]
          (when (> to-explode 0)
            (let [new-result (roll {:exploding? true
                                    :shade (:shade dice)
                                    :exponent to-explode})
                  {new-successes :successes new-rolled :rolled } new-result
                  
                  joined {:successes (+ successes new-successes)
                          :rolled    (concat rolled new-rolled)
                          :dice      (assoc dice :exploding? true)}]

              (pack-result nick joined obstacle))))))))
