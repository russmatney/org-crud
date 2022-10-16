(ns org-crud.core
  (:require
   [clojure.string :as string]
   [babashka.fs :as fs]
   [org-crud.parse :as parse]
   [clojure.set :as set]
   [clojure.walk :as walk]))

(def always-exclude #{"private" "personal" "hide"})

(defn reject-items [item reject-p]
  (when-not (reject-p item)
    (walk/postwalk
      (fn [node]
        (cond
          (not (map? node)) node

          (and (map? node) (seq (:org/items node)))
          (update node :org/items #(remove reject-p %))

          :else node))
      item)))

(defn path->nested-item
  "Parses and returns a single item, the `:root` of the org file,
  with the rest of the nodes/children as `:items`.

  See also: `path->flattened-items`."
  ([p] (path->nested-item nil p))
  ([opts p]
   (let [exclude-tags (:exclude-tags opts #{})
         exclude-tags (set (concat exclude-tags always-exclude))]
     (->
       (parse/parse-file p)
       (reject-items (fn [item]
                       (seq (set/intersection exclude-tags (:org/tags item)))))))))

(comment
  (let [p (str (fs/home) "/todo/projects.org")]
    (path->nested-item p)))

(defn nested-item->flattened-items [nested-item]
  (->>
    nested-item
    (tree-seq (comp seq :org/items) :org/items)
    (remove nil?)
    seq))

(defn path->flattened-items
  "Returns a flattened list of org items in the passed file.

  Produces flattened items, rather than nested.
  Deeper org nodes are still contained within parents,
  so there is some duplication in this structure.

  See `path->nested-items` for a single object with nested items."
  ([p] (path->flattened-items nil p))
  ([opts p]
   (->>
     (path->nested-item opts p)
     (tree-seq (comp seq :org/items) :org/items)
     (remove nil?)
     seq)))

(comment
  (let [p (str (fs/home) "/todo/projects.org")]
    (path->flattened-items p)))


(defn dir->nested-items
  "Returns nested items for every .org file in the passed dir.

  For now, :recursive? only goes one layer down.
  TODO recurse properly."
  ([dir] (dir->nested-items {} dir))
  ([opts dir]
   (->> dir
        fs/list-dir
        (map (fn [f]
               (if (and (:recursive? opts)
                        (not (string/includes? f ".git"))
                        (fs/directory? f))
                 (fs/list-dir f)
                 [f])))
        (apply concat)
        (filter #(contains? #{".org"} (fs/extension %)))
        (map (partial path->nested-item opts))
        (remove nil?))))

(comment
  (-> (dir->nested-items (fs/home "/Dropbox/todos"))
      count)
  (-> (dir->nested-items
        {:recursive? true} (fs/home "/Dropbox/todos"))
      count))
