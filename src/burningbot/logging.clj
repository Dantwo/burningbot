(ns burningbot.logging
  "the logging module provides a number of key services to the bot. Firstly it
   logs messages (per channel) to disk as plain textfiles, and handles
   rotations of those files on a daily basis

   Secondly it provides utilities to access those logs.

   Finally it handles marking sections of logs with various tags. This includes
   a comprehensive parser to unpack messages from IRC.

   Some examples of the marking syntax:

      mark now as sss11
      mark last 30 minutes as sss11, gold edition
      mark the last 30min as gold edition,sss11
      mark last 30m as sss11
      mark last half hour as sss11
      mark 40 seconds ago as sss11, gold edition, bw
      mark 10:30 as sss11
      mark 10:30 to 10:40 with sss11, gold edition
      mark 10:30 - 10:40 with sss11
      mark 10:30 for 10m with sss11, gold edition
      mark 10:30 for quarter of an hour as gold edition, sss11

   All syntax is case insensitive.
  
   There are two primary types of marks: points and spans.
     * Points only have a :start time and represent a single point in the log
     * Spans have a :start and :end time and represent a bracket of messages
       (ie conversation)

   All mark commands share the structure:
     'mark' [time] ('as'|'with') [tags, comma seperated]

   The notation supports defining both types of marks using both relative and
   absolute syntax.

   An absolute mark is declared with a time (hours and minutes, colon seperated) in
   24 hour time notation. Without any additional qualifiers this will define a
   point with the tags that follow. A qualifier can be defined as another absolute
   time or as relative span. An absolute time end is indicated with one of
   the tokens '-'|'to'|'until'|'till', and a relative time end is indicated with the
   token 'for'. Note: in all cases, these absolute times are relative to the bot's
   time zone! your best bet is to use the web logs for reference.

   Relative marks are declared using significantly more flexible syntax than the
   absolute marks. the most simple relative mark is a declared simply with the token
   'now' and indicates an point mark right now.

   The syntax for relative marks either declares a point or a span relative to now.
   the syntax for a point is:
     [relative-time] 'ago'

   While the syntax for a span is:
     'the'? 'last' [relative-time]

   the time description defines an interval of time in seconds, minutes, hours or days.
   This is quite straight forward:
     ([integer] | (('half'|'third'|'quarter') ('of'? 'an')?)) [unit]

   This means you can use a handful of quantitative nouns or literal integers to
   describe a quantity of time, and in any unit you need.

   Please note: this syntax does not have any consideration for date, only time."
  
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.format]
            [burningbot.settings :as settings]
            [burningbot.db :as db]
            [name.choi.joshua.fnparse :as parse])
  (:use [clj-time.format :only [unparse]]
        [name.choi.joshua.fnparse :only [conc lit alt opt rep+]]))

;;;; writing logs

(defn obscure-email
  [^String message]
  (.replaceAll message "(?i)[a-z0-9.-]+@[a-z0-9.]+" "<email obscured>"))

(defn log-filename
  [ts]
  (str/join "-" ((juxt t/year t/month t/day) ts)))

(def time-format (clj-time.format/formatters :hour-minute-second))

(defn log-loop
  "this loop is the core of the logging thread and is largely imperative.
   it maintains appending messages to a log file and rotating to a new long
   as required."
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
  (let [message (obscure-email message)]
    (.put (mailbox-for-channel! channel) [nick message (t/now)])))

;; log writing message handlers

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
        (when (and (string? response)
                   (logged-channels channel))
          (log-message botnick response channel))
        response))))

;;;; retrieving logs

(defn- log-file*
  [channel date]
  (io/file (settings/read-setting [:logging :dir])
           channel
           (log-filename date)))

(defn log-exists?
  [channel date]
  (.exists (log-file* channel date)))

(defn log-file
  "returns a file or nil"
  [channel date]
  (let [log (log-file* channel date)]
    (when (.exists log)
      log)))

(defn log-metadata
  "returns the metadata for a given channel and date"
  [channel date]
  (when (log-exists? channel date)
    {:channel channel
     :marks (db/query-logmarks-for-day channel date)}))

;;;; handle log marking commands

;; the following defines a simple parser using fnparse to process an
;; incoming message for marking instructions and creating a
;; appropriate log mark records

(defn maybe-mark-command?
  "returns true if the first word in the message is 'mark' after
   discarding whitespace and ignoring case.

   This is useful in particular to determine if the parser
   should be run over the text."
  [^String text]
  (-> text .trim (.substring 0 5) (.toLowerCase) (= "mark ")))

;; parsers

(let [time-units {"s"       ::seconds
                  "sec"     ::seconds
                  "secs"    ::seconds
                  "second"  ::seconds
                  "seconds" ::seconds
                  "m"       ::minutes
                  "min"     ::minutes
                  "mins"    ::minutes
                  "minute"  ::minutes
                  "minutes" ::minutes
                  "h"       ::hours
                  "hour"    ::hours
                  "hours"   ::hours
                  "day"     ::days
                  "days"    ::days}]
  (def parse-time-unit
    (parse/semantics (parse/lit-alt-seq (keys time-units))
                     time-units)))

(def parse-integer
  (parse/semantics (parse/re-term #"\d+")
                   #(Integer/parseInt %)))

(let [valid-nouns {"half"    1/2
                   "third"   1/3
                   "quarter" 1/4}]
  (def parse-quantitative-noun
    (parse/semantics
     (parse/invisi-conc (parse/lit-alt-seq (keys valid-nouns))
                        (opt (conc (opt (lit "of")) (lit "an"))))
     valid-nouns)))

(let [units {::seconds 1
             ::minutes 60
             ::hours   3600
             ::day     86400}]
  (defn factor-time
    "given a value and a unit, returns a new time in the seconds clamped to a 1 second minimum"
    [[ratio unit]]
    (max 1 (* (or ratio 1) (units unit)))))

(def parse-value-with-unit
  (parse/semantics
   (alt (conc parse-integer
              parse-time-unit)
        (conc (opt parse-quantitative-noun)
              parse-time-unit))
   factor-time))

(def parse-relative-time-range
  (alt (parse/semantics (conc (opt (lit "the")) (lit "last") 
                              parse-value-with-unit)
                        (fn [[_ _ t]] {:relative t :span true}))
       (parse/semantics (conc parse-value-with-unit
                              (lit "ago"))
                        (fn [[t _]] {:relative t :span false}))
       (parse/constant-semantics (lit "now") {:relative 0 :span false})))

(def parse-moment
  (parse/semantics (conc parse-integer (lit ":") parse-integer)
                   (fn [[m _ s]] [m s])))

(def parse-range
  (parse/semantics
   (conc parse-moment
         (opt (alt (parse/semantics
                    (conc (parse/lit-alt-seq ["to" "until" "till" "-"]) parse-moment)
                    (fn [[_ end]] {:end end}))
                   (parse/semantics
                    (conc (lit "for")
                          parse-value-with-unit)
                    (fn [[_ duration]] {:duration duration})))))
   (fn [range] {:absolute range})))

(def parse-time
  (alt parse-relative-time-range
       parse-range))

(defn group-tags
  "takes a sequence of strings that are tags or commas and returns a seq of tags"
  [tags]
  (->> tags
       (partition-by #{","})
       (take-nth 2)
       (map #(str/join " " %))))

(def parse-tags
  (parse/semantics (conc (parse/lit-alt-seq ["as" "with"])
                         (rep+ parse/anything))
                   (comp group-tags second)))

(defn transform-relative-time
  "transform-relative-time takes a number of seconds and a joda time object to
   calculate the the new time relative to"
  [seconds reference-time span]
  {:start seconds :end (when span reference-time)})

(defn transform-absolute-time
  "takes a time record from the parser and converts it into a joda time object
   that represents that time on the same day as the reference-time.

   If there is no end, it will convert a duration using transform-relative-time
   with the calculated start-time as the reference time.

   If no end or duration is provided, nil is returned as :end."
  [[start {:keys [end duration]}] reference-time]
  (let [start-time start
        end-time   (cond end      end
                         duration (transform-relative-time duration start-time true))]
    {:start start-time :end end-time}))

(defn transform-time
  "transform time takes a parse tree and a reference time and returns a new
   record with :start and :end keys"
  [time reference-time]
  (prn ">" time)
  (cond (:relative time) (transform-relative-time (:relative time) reference-time (:span time))
        (:absolute time) (transform-absolute-time (:absolute time) reference-time)))

(defn run-mark-parser
  "This function is the entry point into the parser. provided a string
   of text it will return a mark record or nil.

   run-mark-parser takes a time to use as a point to offset any relative
   times against. This should be a joda time object."
  [^String text reference-time]
  (let [input (->> text                                  ; tokenize the input message
                   .toLowerCase
                   (re-seq #"([^\d,\s]+|\d+|,)")
                   (map first))
        state {:remainder input}
        always-nil (constantly nil)]
    (parse/rule-match (parse/semantics (conc (lit "mark")
                                             parse-time
                                             parse-tags)
                                       (fn [[_ t tags]]
                                         (assoc (transform-time t reference-time)
                                           :tags  tags)))
                      always-nil always-nil
                      state)))

;; message handlers

(defn handle-logmark
  "Attempts to handle incoming log marking requests. These log marks are viewable via
   on the web application."
  [{:keys [nick channel message]}]
  (when (maybe-mark-command? message)
    (if-let [mark (run-mark-parser message (t/now))]
      (do
        (db/insert-logmark! (assoc mark :channel channel :author nick))
        "mark saved.")
      (str nick ": I'm confused."))))

(comment
  (->> (.split "mark now as sss11
      mark last 30 minutes as sss11, gold edition
      mark the last 30min as gold edition,sss11
      mark last 30m as sss11
      mark last half hour as sss11
      mark 40 seconds ago as sss11, gold edition, bw
      mark 10:30 as sss11
      mark 10:30 to 10:40 with sss11, gold edition
      mark 10:30 - 10:40 with sss11
      mark 10:30 for 10m with sss11, gold edition
      mark 10:30 for quarter of an hour as gold edition, sss11" "\n")
       (map #(.trim %))
       (map (juxt identity #(run-mark-parser % 1)))
       (pprint)))
