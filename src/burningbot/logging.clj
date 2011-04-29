(ns burningbot.logging
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.format]
            [burningbot.settings :as settings])
  (:use [clj-time.format :only [unparse]]))

(defn log-filename
  [ts]
  (str/join "-" ((juxt t/year t/month t/day) ts)))

(defonce log-queue (java.util.concurrent.LinkedBlockingQueue. ))

(def time-format (clj-time.format/formatters :hour-minute-second))

(defn log-loop
  [queue]
  (loop [log-date (t/now)]
    (let [next-day (t/plus (apply t/date-time ((juxt t/year t/month t/day)
                                               log-date))
                           (t/days 1))]
      (with-open [aw (io/writer (io/file (settings/read-setting
                                          [:logging :dir])
                                         (log-filename log-date))
                                :append true)]
        (binding [*out* aw]
          (while (t/before? (t/now) next-day)
            (let [[nick message time] (.take log-queue)]
              (println (unparse time-format time)
                       (str "<" nick ">")
                       message))))
        (recur (t/now))))))

(defonce log-thread
  (doto
      (Thread.
       (partial log-loop log-queue))
    (.setDaemon true)
    (.start)))


(defn log-message
  "This function sends a message to the logger agent to write to a log file"
  [nick message]
  (.put log-queue [nick message (t/now)]))

(let [logged-channels (settings/read-setting [:logging :channels])]
  (defn handle-logging
    [{:keys [nick message channel]}]
    (when (logged-channels channel)
      (log-message nick message))))
