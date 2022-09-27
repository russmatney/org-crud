(ns org-crud.core
  (:require
   [clojure.string :as string]
   [babashka.fs :as fs]
   [org-crud.parse :as parse]))

(defn path->nested-item
  "Parses and returns a single item, the `:root` of the org file,
  with the rest of the nodes/children as `:items`.

  See also: `path->flattened-items`."
  [p] (parse/parse-file p))

(comment
  (let [p (str (fs/home) "/todo/projects.org")]
    (path->nested-item p)))


(defn path->flattened-items
  "Returns a flattened list of org items in the passed file.

  Produces flattened items, rather than nested.
  Deeper org nodes are still contained within parents,
  so there is some duplication in this structure.

  See `path->nested-items` for a single object with nested items."
  [p]
  (tree-seq (comp seq :org/items) :org/items (path->nested-item p)))

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
        (map path->nested-item)
        (remove nil?))))

(comment
  (-> (dir->nested-items (fs/home "/Dropbox/todos"))
      count)
  (-> (dir->nested-items
        {:recursive? true} (fs/home "/Dropbox/todos"))
      count))
