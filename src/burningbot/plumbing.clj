(ns burningbot.plumbing
  "plumping provides tools for combining irc message handler functions. This is use to compose an overall
   handler"
  (:require [irclj.core :as irclj]))

;; handler combinators

(defn guard
  "All the predicates listed in preds must return a truthy value for f to be called."
  [preds handler]
  (fn [info]
    (when-not (some false? (map #(% info) preds))
      (handler info))))

(defn first-of [fs]
  "first-of tries each function in the argument sequence in order, and returns the
   result of the first non-nil response"
  (let [fs (apply list fs)] ; we dont want to process the message with
                            ; more functions than necessary, and if a
                            ; vector is passed in we get a chunked seq
    (fn [{:keys [irc channel] :as all}]
      (first (keep #(% all) fs)))))

(defn send-response-as-message
  "if the handler returns a string then that its sent as a message to the originating channel."
  [handler]
  (fn [{:keys [irc channel] :as info}]
    (when-let [response (handler info)]
      (when (string? response)
        (irclj/send-message irc channel response))
      response)))

;; the following functions deal with addressed commands

(defn nick-address [s]
  "returns a string for a nick or nil"
  (let [s   (.trim s)
        len (.length s)
        last-index (dec len)]
    (when (and (> len 0)
               (= \: (.charAt s last-index)))
      (.substring s 0 last-index))))

(defn strip-nick-address
  [first-piece nick message]
  (if (= nick (nick-address first-piece))
    (-> message
        (.substring (.length first-piece))
        (.trim))
    message))

(defn addressed-command
  "an addressed-command only fires if the first piece is '_botnick_:' or the channel is private"
  [handler]
  (fn [{:keys [channel pieces irc message] :as all}]
    (let [botname (:name (dosync @irc))]
      (if (not= (.charAt channel 0) \#)
        (handler all) ; is a private message channel, no need for address
        (when-let [addr-nick (nick-address (first pieces))]
          (when (= botname addr-nick)
            (handler (assoc all                 :pieces (rest pieces)
                            :message (strip-nick-address (first pieces)
                                                         botname
                                                         message)))))))))

(defn ignore-address
  "If an address appears at the start of the message strip it out."
  [handler]
  (fn [{:keys [message pieces irc] :as all}]
    (let [botname (:name (dosync @irc))
          new-message (strip-nick-address (first pieces) botname message)]
      (if (= message new-message)
        (handler all)
        (handler (assoc all :pieces (rest pieces)
                  :message new-message))))))

