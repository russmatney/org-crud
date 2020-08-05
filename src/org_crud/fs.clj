(ns org-crud.fs
  "Subset of me.raynes.fs (clj-commons.fs) ripped for bb compatibility."
  (:refer-clojure :exclude [name parents])
  (:require [clojure.java.io :as io])
  (:import [java.io File]))

(def ^{:doc     "Current working directory. This cannot be changed in the JVM.
             Changing this will only change the working directory for functions
             in this library."
       :dynamic true}
  *cwd* (.getCanonicalFile (io/file ".")))

(defn ^File file
  "If path is a period, replaces it with cwd and creates a new File object
   out of it and paths. Or, if the resulting File object does not constitute
   an absolute path, makes it absolutely by creating a new File object out of
   the paths and cwd."
  [path & paths]
  (when-let [path (apply
                    io/file (if (= path ".")
                              *cwd*
                              path)
                    paths)]
    (if (.isAbsolute ^File path)
      path
      (io/file *cwd* path))))

(defn ^String base-name
  "Return the base name (final segment/file part) of a path.

   If optional `trim-ext` is a string and the path ends with that string,
   it is trimmed.

   If `trim-ext` is true, any extension is trimmed."
  ([path] (.getName (file path)))
  ([path trim-ext]
   (let [base (.getName (file path))]
     (cond (string? trim-ext) (if (.endsWith base trim-ext)
                                (subs base 0 (- (count base) (count trim-ext)))
                                base)
           trim-ext           (let [dot (.lastIndexOf base ".")]
                                (if (pos? dot) (subs base 0 dot) base))
           :else              base))))


(defn absolute
  "Return absolute file."
  [path]
  (.getAbsoluteFile (file path)))

(defn list-dir
  "List files and directories under path."
  [path]
  (seq (.listFiles (file path))))

(defn split-ext
  "Returns a vector of [name extension]."
  [path]
  (let [base (base-name path)
        i    (.lastIndexOf base ".")]
    (if (pos? i)
      [(subs base 0 i) (subs base i)]
      [base nil])))

(defn extension
  "Return the extension part of a file."
  [path] (last (split-ext path)))

(defn directory?
  "Return true if `path` is a directory."
  [path]
  (if (file path)
    (.isDirectory (file path))
    false))

(defn create
  "Create a new file."
  [^File f]
  (.createNewFile f))

(defn touch
  "Set file modification time (default to now). Returns path."
  [path & [time]]
  (let [f (file path)]
    (when-not (create f)
      (.setLastModified f (or time (System/currentTimeMillis))))
    f))

(defn exists?
  "Return true if `path` exists."
  [path]
  (. (file path) exists))
