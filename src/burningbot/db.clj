(ns burningbot.db
  "database functions for burningbot"
  (:require [clojureql.core :as ql]
            [burningbot.settings :as settings]))

(def db (settings/read-setting :database))

(defmacro Q
  "This macro elides the body of any query if db settings are not available."
  ([body] `(Q ~body ()))
  ([body default]
     `(if db
        (do ~body)
        ~default)))


;; tables
(def facts (ql/table db :facts))

(def logmarks (ql/table db :logmarks))

(def tags (ql/table db :tags))

(def tag-refs (ql/table db :tag_ref))

(def content-types {::facts "f"
                    ::logmarks "l"})


;; facts
(defn- fact-predicate
  "where clause to limit fact selection by channel and key"
  [channel key]
  (ql/where (and (= :channel channel)
                 (= :name key))))

(defn query-fact
  "returns all facts that match the channel and key"
  [channel key]
  (Q @(-> facts
          (ql/select (fact-predicate channel key))
          (ql/project [:value]))))

(defn insert-fact!
  "insert or update a facto for the channel and key"
  [channel key value creator]
  (Q (ql/update-in! facts
                    (fact-predicate channel key)
                    {:name key :channel channel :value value :creator creator})))

(defn delete-fact!
  "removes a fact from the database for the given channel and key"
  [channel key]
  (Q (ql/disj! facts (fact-predicate channel key))))

(defn query-fact-names
  "returns a seq of all the fact names for this channel"
  [channel]
  (Q @(ql/project facts [:name])))


;; tags
(defn query-tagged
  "returns the ids of all the items for the content type that match the given tag"
  [tag content-type]
  (let [content-type (content-types content-type)]
    (-> tags
        (ql/select (ql/where (= :tag tag)))
        (ql/join tag-refs (ql/where (and (= :id :tag_id)
                                         (= :content_type content-type))))
        (ql/project [:tag_ref.content_id]))))

(defn query-facts-by-tag
  "returns all the facts for a given tag and channel"
  [channel tag]
  (Q (-> (query-tagged tag ::facts)
         (ql/join facts (ql/where (= :facts.id :content_id)))
         (ql/select (ql/where (= :channel channel)))
         (ql/project [:facts.name :facts.value :facts.creator :facts.channel]))))
