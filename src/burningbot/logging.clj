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

(def time-format (clj-time.format/formatters :hour-minute-second))

(defn log-loop
  [queue channel-name]
  (loop [log-date (t/now)]
    (let [next-day (t/plus (apply t/date-time ((juxt t/year t/month t/day)
                                               log-date))
                           (t/days 1))
          file-args [(settings/read-setting [:logging :dir])
                     channel-name
                     (log-filename log-date)]]
      (apply io/make-parents file-args)
      
      (with-open [aw (io/writer (apply io/file file-args)
                                :append true)]
        (binding [*out* aw]
          (while (t/before? (t/now) next-day)
            (let [[nick message time] (.take queue)]
              (println (unparse time-format time)
                       (str "<" nick ">")
                       message))))
        (recur (t/now))))))

(defn new-logger
  "Creates a new logger thread for the given channel name"
  [^String channel-name]
  (let [channel-name (.toLowerCase (if (.startsWith channel-name "#")
                                     (.substring channel-name 1)
                                     channel-name))
        log-queue  (java.util.concurrent.LinkedBlockingQueue. )
        log-thread (doto
                       (Thread.
                        (partial log-loop log-queue channel-name))
                     (.setDaemon true)
                     (.start))]
    log-queue))

(defonce logger-mailboxes (atom {}))

(defn mailbox-for-channel!
  [channel-name]
  (or (get @logger-mailboxes channel-name)
      (get (swap! logger-mailboxes assoc channel-name (new-logger channel-name)) channel-name)))

(defn log-message
  "This function sends a message to the logger agent to write to a log file"
  [nick message channel]
  (.put (mailbox-for-channel! channel) [nick message (t/now)]))

(let [logged-channels (settings/read-setting [:logging :channels])]
  (defn handle-logging
    "this handler logs any message on a logged channel"
    [{:keys [nick message channel]}]
    (when (logged-channels channel)
      (log-message nick message channel)))

  (defn log-handler-response
    "the log-handler-response middleware takes a handler and returns a new handler that logs the result
     returned from that handler using the bots nick."
    [handler]
    (fn [{:keys [irc channel] :as info}]
      (let [response (handler info)
            botnick  (@irc :name)]
        (when (and response
                   (logged-channels channel))
          (log-message botnick response channel))
        response))))
