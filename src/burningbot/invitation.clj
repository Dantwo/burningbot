(ns burningbot.invitation
  "lets irc users invite burningbot into their channel.

   This modules primary usecase is to make burningbot a polite irc citizen and prevent abuse.
   There are three classes of channels:
     * common rooms; those rooms that are specified by the bot maintainer in its settings.
     * invited but unauthorized rooms; a room where an untrusted irc user has requested the bot join
         but that user is not an op in that room. burningbot will notifiy the room that it is
         remaining silent till authorized by a channel op. should also time out.
     * invited and authorized rooms; burningbot behaves as normal.

   In addition to this, if burningbot is invited to a channel and the inviting user is not present, he
   will leave and mark that channel and user pair as not valid for a duration.
   "
  (:require [irclj.core :as irclj]
            [burningbot.settings :as settings]))


(defonce
  ^{:doc "authorization-required is a reference to a map of channels and the authorizations."}
  authorizations
  (ref {}))


(defonce
  ^{:doc "restrictions is a ref to a map of users listing the restrictions that burningbot has placed
          on them. This is to prevent abuse and manage authentication etc."}
  user-restrictions
  (ref {}))


(defn set-channel-pending
  "a room is pending while the bot is joining"
  [channel nick]
  (dosync (alter authorizations assoc channel
                                {:state      :pending
                                 :invited-by nick})))

(defn nick-has-ops?
  [irc channel nick]
  (dosync
   (contains? #{"+q" "+o"} (get-in @irc [:channels channel :users nick :mode]))))


(defn confirm-authorization
  ""
  [irc channel invited-by]
  (dosync
   (alter authorizations assoc-in [channel :state]
          (if (nick-has-ops? irc channel invited-by) :speaking :silent))))


(defn begin-authorization
  "actions to undertake when the bot is invited to a room"
  [irc channel]
  (dosync
   (let [users        (-> @irc :channels (get channel) :users)
         inviter-nick (get-in @authorizations [channel :invited-by])]

     (println "\n" (contains? users inviter-nick) users inviter-nick)
     
     (if-not (contains? users inviter-nick)
       (irclj/send-message irc inviter-nick
                           (str "You invited me to join the channel "
                                channel
                                " but you are not there yourself"))
       (do (irclj/send-message irc inviter-nick "thanks for the invitation")
           (confirm-authorization irc channel inviter-nick))))))


;; irc hooks

(defn authorized-for-channel?
  "checks the channel against the authorizations data."
   [{:keys [channel]}]
   (or (contains? (set (settings/read-setting :starting-channels)) channel)
       (= :speaking
          (get-in @authorizations [channel :state]))))

(defn handle-invite
  "parses incoming messages and checks for join and part requests."
  [{:keys [nick channel pieces irc]}]
  (let [cmd     (-> pieces first .toLowerCase)
        to-join (second pieces)]
    (case cmd
          "join" (when-not (and (get-in @irc [:channels to-join])
                                (dosync (get @authorizations channel)))
                   (set-channel-pending to-join nick)
                   (irclj/join-chan irc to-join))
          "part" (irclj/part-chan irc (or to-join channel))
          "auth" (dosync "todo")
          nil)))


(defn handle-join
    "handle join does a large amount of the work for invitations.

   when the bot joins a channel, it begins the authorization process. To do this it requires a list
   of the current users in the channel; because of when on-join fires the bots channel map has not been
   updated yet; to resolve this a future is fired that fetches the names."
    [{:keys [irc channel]}]
    (future
      (irclj/get-names irc channel)     
      (begin-authorization irc channel)))
