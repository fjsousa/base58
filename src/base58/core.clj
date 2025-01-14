(ns base58.core
  "Functions for encoding byte arrays to Bitcoin addresses/WIF keys, and back."
  (:import java.security.MessageDigest)
  (:require [clojure.set :refer [subset?]]))

(def a 123)

;; The Bitcoin base58 alphabet
(def code-string "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

(defn int-to-base58
  "Encodes an integer into a base58 string."
  [num leading-zeros]
  (loop [acc []
         n num]
    (if (pos? n)
      (let [i (rem n 58)
            s (nth code-string i)]
        (recur (cons s acc)
               (quot n 58)))
      (apply str (concat
                  (repeat leading-zeros (first code-string))
                  acc)))))

(defn encode
  "Encodes a byte array into a base58 string."
  [bytes]
  (let [leading-zeros (->> bytes
                           (take-while zero?)
                           count)
        n (java.math.BigInteger. 1 (byte-array bytes))]
    (int-to-base58 n leading-zeros)))

(defn decode
  "Decodes a base58-encoded string into a byte array.
   Returns nil if the string contains invalid base58 characters."
  [base58string]
  (when (subset? (set base58string) (set code-string))
    (let [padding (->> base58string
                       (take-while #(= % (first code-string)))
                       (map (constantly (byte 0))))]
      (loop [result 0
             s base58string]
        (if (seq s)
          (recur (+ (*' result 58) (.indexOf code-string (str (first s))))
                 (rest s))
          (->> result
               str
               java.math.BigInteger.
               .toByteArray
               (drop-while zero?)
               (concat padding)
               byte-array))))))

(defn checksum
  "Computes a Bitcoin-style double sha256 hash of a byte array, returns the first 4 bytes."
  [bytes]
  (let [md (MessageDigest/getInstance "SHA-256")
        hash1 (.digest md bytes)]
    (take 4 (.digest md hash1))))

(defn check
  "True iff the last four bytes of a byte array are the checksum for the first part."
  [bytes]
  (let [c1 (take-last 4 bytes)
        c2 (->> bytes
                (drop-last 4)
                byte-array
                checksum)]
    (= c1 c2)))

(defn encode-check
  "Converts a byte array into a bitcoin address (or key) with a header and a checksum."
  [hash header-byte]
  (let [with-header (byte-array (cons header-byte hash))
        checked (->> with-header
                     checksum
                     (concat with-header))]
    (encode checked)))

(defn get-payload
  "Drops the header and checksum. Call this method after decoding a key
   or an address, to obtain the 20-byte pubKey hash or the 32-byte key."
  [bytes]
  (->> bytes
       (drop-last 4)
       next
       byte-array))

(defn valid?
  "True iff a checksum-encoded string (key/address) has a valid checksum."
  [s]
  (-> s decode check))
