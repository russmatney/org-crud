(ns org-crud.core
  (:require
   [clojure.string :as string]
   [babashka.fs :as fs]
   [org-crud.parse :as parse]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; path or dir to org items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn path->nested-item
  "Produces a single item, the :root of the org file.
  Level-1 sections can be found as :items, all children are nested as
  further lists of :items all the way down.

  If you want the org file as a flattened list, see `path->flattened-items`.
  "
  [p]
  (some->> p parse/parse-file first))

(defn dir->nested-items
  "For now, :recursive? only goes one layer down.

  TODO impl with loop to recur properly."
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
        (map path->nested-item)
        (remove nil?))))

(comment
  (-> (dir->nested-items "/home/russ/Dropbox/notes")
      count)
  (-> (dir->nested-items {:recursive? true} "/home/russ/Dropbox/notes")
      count)

  (-> (dir->nested-items "/home/russ/Dropbox/notes")
      first))

(defn path->flattened-items
  "Returns a flattened list of org items in the passed file.

  Produces flattened items, rather than nested.
  This means deeper org nodes will not be contained within parents.
  See `path->nested-items` for a single object with nested items.

  This function runs `path->nested-items`, then flattens the items for you.
  This is to ensure fields available in the flattened->nested path are set
  (:org/parent-name, :org/parent-ids, :org/relative-index).
  "
  [p]
  (tree-seq (comp seq :org/items) :org/items (path->nested-item p)))

(comment
  (let [p "/Users/russ/todo/projects.org"]
    (path->flattened-items p)))
