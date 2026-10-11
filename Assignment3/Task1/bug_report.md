# StringConcatenation Debugging Exercise

**Student:** Murtadha Ghanim

## Overview
I compared the documented `stringconcatenation` request/response contract in `README.md` with both `SockClient.java` and `SockServer.java`. The assignment requires at least four protocol violations; I found and corrected five related problems so the service now follows the documented field names and response structure and rejects missing/wrong string inputs cleanly.

## Bug #1: Client sent the wrong first-string field name
**Location:** `SockClient.java`, string-concatenation branch in `main`

**The problem:** The client sent `"str1"`, but the protocol requires `"string1"`.

**The fix:** Changed the request field to `json.put("string1", str1)`.

**Why it matters:** A standards-compliant server looks for `string1`, so the old client request was missing a required protocol field.

**Test:** Enter first string `Hello ` and second string `World`. The client now sends:
```json
{"type":"stringconcatenation","string1":"Hello ","string2":"World"}
```

## Bug #2: Server did not validate `string2`
**Location:** `SockServer.java`, `concat`

**The problem:** The server checked only `string1`, then immediately called `getString("string2")`. A request with no `string2` could throw instead of returning the documented error response.

**The fix:** Added `testField(req, "string2")` before reading the value.

**Why it matters:** Required fields must be validated and invalid requests must return an `ok:false` protocol response rather than crashing request processing.

**Test:**
```json
{"type":"stringconcatenation","string1":"Hello"}
```
returns an error stating that `string2` does not exist in the request.

## Bug #3: Server returned the wrong response type
**Location:** `SockServer.java`, `concat`

**The problem:** The success response used `"type":"concat"`.

**The fix:** Changed it to `"type":"stringconcatenation"`.

**Why it matters:** The protocol says the response type must be `stringconcatenation`. Clients use this field to determine how to interpret the response.

**Test:** A valid concatenation response now contains:
```json
{"type":"stringconcatenation","ok":true,"result":"HelloWorld"}
```

## Bug #4: Server returned the wrong result field
**Location:** `SockServer.java`, `concat`

**The problem:** The server placed the concatenated value in a field named `combined`.

**The fix:** Changed the response field to `result`.

**Why it matters:** The documented success response requires `result`. A client written from the protocol would not know to read `combined`.

**Test:**
```json
{"type":"stringconcatenation","string1":"abc","string2":"123"}
```
returns `"result":"abc123"`.

## Bug #5: Client treated every non-echo success result as an integer
**Location:** `SockClient.java`, response-handling branch in `main`

**The problem:** The client called `res.getInt("result")` for every successful response other than `echo`. A correct string-concatenation response has a String `result`, so the client would fail while reading it.

**The fix:** Added response handling based on the returned `type`. `add` reads an integer result, while `stringconcatenation` reads a String result. Currency responses are also handled separately.

**Why it matters:** Correct protocol messages are only useful if the client decodes each field using the documented type.

**Test:** Concatenating `abc` and `123` now prints `Result: abc123` instead of trying to parse the value as an integer.

## Additional validation improvement
The server now verifies that both `string1` and `string2` are JSON strings. If either has another JSON type, it returns:
```json
{"type":"stringconcatenation","ok":false,"message":"Fields string1/string2 need to be of type: String"}
```
