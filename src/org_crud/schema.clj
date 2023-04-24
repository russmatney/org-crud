(ns org-crud.schema
  (:require
   [malli.core :as m]
   [malli.transform :as mt]))

(def item-schema
  [:map
   [:org/name :string]
   [:org/name-string :string]
   [:org/level-int :int]
   [:org/id {:optional true} :uuid]
   [:org/status {:optional true} :keyword]
   [:org/tags {:optional true} [:set :string]]
   [:org/word-count {:optional true} :int]

   #_[:org/items {:optional true} [:vec item-schema]]
   ])

(defn strip [it]
  (m/decode item-schema it (mt/strip-extra-keys-transformer)))

(defn validate [it]
  (m/validate item-schema it))

(comment
  (validate
    {:org/name "hi"})

  (validate
    {:org/tags #{"hi"}
     :org/name "sup"})

  (strip
    {:org/tags #{"hi"}
     :org/name "sup"
     :misc     "other stuff"}))
