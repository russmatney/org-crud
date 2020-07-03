(ns org-crud.fs
  "Subset of me.raynes.fs (clj-commons.fs) ripped for bb compatibility.
  File system utilities in Clojure"
  (:refer-clojure :exclude [name parents])
  (:require [clojure.java.io :as io])
  (:import [java.io File]))

;; Once you've started a JVM, that JVM's working directory is set in stone
;; and cannot be changed. This library will provide a way to simulate a
;; working directory change. `cwd` is considered to be the current working
;; directory for functions in this library. Unfortunately, this will only
;; apply to functions inside this library since we can't change the JVM's
;; actual working directory.
(def ^{:doc     "Current working directory. This cannot be changed in the JVM.
             Changing this will only change the working directory for functions
             in this library."
       :dynamic true}
  *cwd* (.getCanonicalFile (io/file ".")))

;; Library functions will call this function on paths/files so that
;; we get the cwd effect on them.
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
