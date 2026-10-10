# Task 1: Mystery Service Discovery and Protocol Documentation

**Student:** Murtadha Ghanim

**How I tested:** Controlled requests against the provided `mysteryservice.jar`, plus automated protocol tests in `ServerTest.java`.

---

## Part 1: Discovery Log

### Attempt 1 - Minimal analyzer request
**Request Sent:**
```json
{"type":"analyzer"}
```

**Response Received:**
```json
{"ok":false,"message":"Field 'action' does not exist in request. Hint: what action do you want to perform?"}
```

**What I Learned:** The service requires an `action` field.

### Attempt 2 - Try a likely action without text
**Request Sent:**
```json
{"type":"analyzer","action":"wordcount"}
```

**Response Received:**
```json
{"ok":false,"message":"Field 'text' does not exist in request"}
```

**What I Learned:** `wordcount` is recognized and requires a `text` field.

### Attempt 3 - Unsupported action
**Request Sent:**
```json
{"type":"analyzer","action":"reverse","text":"hello"}
```

**Response Received:**
```json
{"ok":false,"message":"Action 'reverse' not supported. Valid actions: wordcount, charcount, search"}
```

**What I Learned:** The complete action list is `wordcount`, `charcount`, and `search`.

### Attempt 4 - Word count success
**Request Sent:**
```json
{"type":"analyzer","action":"wordcount","text":"one two   three"}
```

**Response Received:**
```json
{"type":"analyzer","ok":true,"action":"wordcount","count":3}
```

**What I Learned:** `wordcount` splits on whitespace and returns the number of words in `count`.

### Attempt 5 - Character count success
**Request Sent:**
```json
{"type":"analyzer","action":"charcount","text":"hello world"}
```

**Response Received:**
```json
{"type":"analyzer","ok":true,"action":"charcount","count":11}
```

**What I Learned:** `charcount` counts every character in the String, including spaces.

### Attempt 6 - Search without a target
**Request Sent:**
```json
{"type":"analyzer","action":"search","text":"banana"}
```

**Response Received:**
```json
{"ok":false,"message":"Field 'find' does not exist in request"}
```

**What I Learned:** `search` has one additional required String field named `find`.

### Attempt 7 - Search with an empty target
**Request Sent:**
```json
{"type":"analyzer","action":"search","text":"banana","find":""}
```

**Response Received:**
```json
{"ok":false,"message":"Field 'find' cannot be empty"}
```

**What I Learned:** The `find` String must not be empty.

### Attempt 8 - Search for overlapping matches
**Request Sent:**
```json
{"type":"analyzer","action":"search","text":"banana","find":"ana"}
```

**Response Received:**
```json
{"type":"analyzer","ok":true,"action":"search","find":"ana","found":true,"count":2,"positions":[1,3]}
```

**What I Learned:** Search returns zero-based match positions and includes overlapping matches (`ana` occurs at indices 1 and 3).

### Attempt 9 - Search target not present
**Request Sent:**
```json
{"type":"analyzer","action":"search","text":"banana","find":"xyz"}
```

**Response Received:**
```json
{"type":"analyzer","ok":true,"action":"search","find":"xyz","found":false,"count":0,"positions":[]}
```

**What I Learned:** A search with no matches is still a successful request; `found` is false and `positions` is empty.

### Attempt 10 - Action capitalization
**Request Sent:**
```json
{"type":"analyzer","action":"WORDCOUNT","text":"A B"}
```

**Response Received:**
```json
{"type":"analyzer","ok":true,"action":"wordcount","count":2}
```

**What I Learned:** Action names are case-insensitive and the response normalizes the action name to lowercase.

---

## Part 2: Complete Protocol Specification

### Analyzer Service
The analyzer service performs simple operations on a String supplied in `text`. All requests use `"type":"analyzer"`. The `action` field is case-insensitive, while text matching performed by `search` is case-sensitive.

### Operation: wordcount
**Request:**
```json
{
  "type":"analyzer",
  "action":"wordcount",
  "text":"<String>"
}
```

**Success Response:**
```json
{
  "type":"analyzer",
  "ok":true,
  "action":"wordcount",
  "count":<int>
}
```

An empty or whitespace-only `text` has a word count of `0`.

### Operation: charcount
**Request:**
```json
{
  "type":"analyzer",
  "action":"charcount",
  "text":"<String>"
}
```

**Success Response:**
```json
{
  "type":"analyzer",
  "ok":true,
  "action":"charcount",
  "count":<int>
}
```

The count is the Java String length, so spaces and punctuation count as characters.

### Operation: search
**Request:**
```json
{
  "type":"analyzer",
  "action":"search",
  "text":"<String>",
  "find":"<non-empty String>"
}
```

**Success Response:**
```json
{
  "type":"analyzer",
  "ok":true,
  "action":"search",
  "find":"<String>",
  "found":<boolean>,
  "count":<int>,
  "positions":[<zero-based integer positions>]
}
```

The implementation checks the next possible starting index after each match, so overlapping matches are included.

### Error responses
Missing action:
```json
{"ok":false,"message":"Field 'action' does not exist in request. Hint: what action do you want to perform?"}
```

Missing text:
```json
{"ok":false,"message":"Field 'text' does not exist in request"}
```

Unsupported action:
```json
{"ok":false,"message":"Action '<action>' not supported. Valid actions: wordcount, charcount, search"}
```

Search missing `find`:
```json
{"ok":false,"message":"Field 'find' does not exist in request"}
```

Search with empty `find`:
```json
{"ok":false,"message":"Field 'find' cannot be empty"}
```

**Wrong JSON value types:** The binary uses String getters for `action`, `text`, and `find` without its own type-error response. Supplying a non-String value for one of those fields raises a JSON type exception instead of producing a normal analyzer error object. This is a limitation of the provided mystery-service implementation.

---

## Part 3: Summary

**Total Operations Discovered:** 3 (`wordcount`, `charcount`, `search`)

**How I approached discovery:** I began with the smallest valid outer request and used each error message to add one field at a time. After discovering the action names, I tested each success path, then missing/empty fields, capitalization, a no-match search, and overlapping matches.

**Most challenging part:** The search response required testing more than one example to determine that positions are zero-based and overlapping matches are included.

**Client/tests approach:** I chose automated tests. `ServerTest.java` contains analyzer requests and assertions for the discovered response fields.
