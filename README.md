# backgroundlocation

Es un plugin para poder obtener la ubicacion del dispositivo en tiempo real

## Install

```bash
npm install backgroundlocation
npx cap sync
```

## API

<docgen-index>

* [`setMotivo(...)`](#setmotivo)
* [`echo(...)`](#echo)
* [`panic()`](#panic)
* [`status(...)`](#status)
* [`getBitacora(...)`](#getbitacora)
* [`getTramas(...)`](#gettramas)
* [`clearTramas(...)`](#cleartramas)
* [`startService(...)`](#startservice)
* [`stopService()`](#stopservice)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### setMotivo(...)

```typescript
setMotivo(motivo: { code: number; message: string; }) => void
```

| Param        | Type                                            |
| ------------ | ----------------------------------------------- |
| **`motivo`** | <code>{ code: number; message: string; }</code> |

--------------------


### echo(...)

```typescript
echo(options: { value: string; }) => Promise<{ value: string; }>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ value: string; }</code> |

**Returns:** <code>Promise&lt;{ value: string; }&gt;</code>

--------------------


### panic()

```typescript
panic() => void
```

--------------------


### status(...)

```typescript
status(callback: (status: boolean) => void) => void
```

| Param          | Type                                      |
| -------------- | ----------------------------------------- |
| **`callback`** | <code>(status: boolean) =&gt; void</code> |

--------------------


### getBitacora(...)

```typescript
getBitacora(callback: (bitacora: any[]) => void) => void
```

| Param          | Type                                      |
| -------------- | ----------------------------------------- |
| **`callback`** | <code>(bitacora: any[]) =&gt; void</code> |

--------------------


### getTramas(...)

```typescript
getTramas(callback: (tramas: any) => void) => void
```

| Param          | Type                                  |
| -------------- | ------------------------------------- |
| **`callback`** | <code>(tramas: any) =&gt; void</code> |

--------------------


### clearTramas(...)

```typescript
clearTramas(callback: () => void) => void
```

| Param          | Type                       |
| -------------- | -------------------------- |
| **`callback`** | <code>() =&gt; void</code> |

--------------------


### startService(...)

```typescript
startService(options: WatcherOptions, callback: (position?: Location | undefined, error?: Object | undefined) => void) => Promise<string>
```

| Param          | Type                                                                                                        |
| -------------- | ----------------------------------------------------------------------------------------------------------- |
| **`options`**  | <code><a href="#watcheroptions">WatcherOptions</a></code>                                                   |
| **`callback`** | <code>(position?: <a href="#location">Location</a>, error?: <a href="#object">Object</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;string&gt;</code>

--------------------


### stopService()

```typescript
stopService() => Promise<string>
```

**Returns:** <code>Promise&lt;string&gt;</code>

--------------------


### Interfaces


#### WatcherOptions

| Prop                      | Type                                              |
| ------------------------- | ------------------------------------------------- |
| **`notificationTitle`**   | <code><a href="#string">String</a></code>         |
| **`notificationContent`** | <code><a href="#string">String</a></code>         |
| **`minS`**                | <code>number</code>                               |
| **`urlRequests`**         | <code><a href="#string">String</a> \| null</code> |
| **`inBG`**                | <code>boolean</code>                              |
| **`distanceFilter`**      | <code>number</code>                               |
| **`userId`**              | <code>string</code>                               |
| **`authorization`**       | <code>string \| null</code>                       |
| **`imei`**                | <code>string</code>                               |


#### String

Allows manipulation and formatting of text strings and determination and location of substrings within strings.

| Prop         | Type                | Description                                                  |
| ------------ | ------------------- | ------------------------------------------------------------ |
| **`length`** | <code>number</code> | Returns the length of a <a href="#string">String</a> object. |

| Method                | Signature                                                                                                                      | Description                                                                                                                                   |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| **toString**          | () =&gt; string                                                                                                                | Returns a string representation of a string.                                                                                                  |
| **charAt**            | (pos: number) =&gt; string                                                                                                     | Returns the character at the specified index.                                                                                                 |
| **charCodeAt**        | (index: number) =&gt; number                                                                                                   | Returns the Unicode value of the character at the specified location.                                                                         |
| **concat**            | (...strings: string[]) =&gt; string                                                                                            | Returns a string that contains the concatenation of two or more strings.                                                                      |
| **indexOf**           | (searchString: string, position?: number \| undefined) =&gt; number                                                            | Returns the position of the first occurrence of a substring.                                                                                  |
| **lastIndexOf**       | (searchString: string, position?: number \| undefined) =&gt; number                                                            | Returns the last occurrence of a substring in the string.                                                                                     |
| **localeCompare**     | (that: string) =&gt; number                                                                                                    | Determines whether two strings are equivalent in the current locale.                                                                          |
| **match**             | (regexp: string \| <a href="#regexp">RegExp</a>) =&gt; <a href="#regexpmatcharray">RegExpMatchArray</a> \| null                | Matches a string with a regular expression, and returns an array containing the results of that search.                                       |
| **replace**           | (searchValue: string \| <a href="#regexp">RegExp</a>, replaceValue: string) =&gt; string                                       | Replaces text in a string, using a regular expression or search string.                                                                       |
| **replace**           | (searchValue: string \| <a href="#regexp">RegExp</a>, replacer: (substring: string, ...args: any[]) =&gt; string) =&gt; string | Replaces text in a string, using a regular expression or search string.                                                                       |
| **search**            | (regexp: string \| <a href="#regexp">RegExp</a>) =&gt; number                                                                  | Finds the first substring match in a regular expression search.                                                                               |
| **slice**             | (start?: number \| undefined, end?: number \| undefined) =&gt; string                                                          | Returns a section of a string.                                                                                                                |
| **split**             | (separator: string \| <a href="#regexp">RegExp</a>, limit?: number \| undefined) =&gt; string[]                                | Split a string into substrings using the specified separator and return them as an array.                                                     |
| **substring**         | (start: number, end?: number \| undefined) =&gt; string                                                                        | Returns the substring at the specified location within a <a href="#string">String</a> object.                                                 |
| **toLowerCase**       | () =&gt; string                                                                                                                | Converts all the alphabetic characters in a string to lowercase.                                                                              |
| **toLocaleLowerCase** | (locales?: string \| string[] \| undefined) =&gt; string                                                                       | Converts all alphabetic characters to lowercase, taking into account the host environment's current locale.                                   |
| **toUpperCase**       | () =&gt; string                                                                                                                | Converts all the alphabetic characters in a string to uppercase.                                                                              |
| **toLocaleUpperCase** | (locales?: string \| string[] \| undefined) =&gt; string                                                                       | Returns a string where all alphabetic characters have been converted to uppercase, taking into account the host environment's current locale. |
| **trim**              | () =&gt; string                                                                                                                | Removes the leading and trailing white space and line terminator characters from a string.                                                    |
| **substr**            | (from: number, length?: number \| undefined) =&gt; string                                                                      | Gets a substring beginning at the specified location and having the specified length.                                                         |
| **valueOf**           | () =&gt; string                                                                                                                | Returns the primitive value of the specified object.                                                                                          |


#### RegExpMatchArray

| Prop        | Type                |
| ----------- | ------------------- |
| **`index`** | <code>number</code> |
| **`input`** | <code>string</code> |


#### RegExp

| Prop             | Type                 | Description                                                                                                                                                          |
| ---------------- | -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`source`**     | <code>string</code>  | Returns a copy of the text of the regular expression pattern. Read-only. The regExp argument is a Regular expression object. It can be a variable name or a literal. |
| **`global`**     | <code>boolean</code> | Returns a Boolean value indicating the state of the global flag (g) used with a regular expression. Default is false. Read-only.                                     |
| **`ignoreCase`** | <code>boolean</code> | Returns a Boolean value indicating the state of the ignoreCase flag (i) used with a regular expression. Default is false. Read-only.                                 |
| **`multiline`**  | <code>boolean</code> | Returns a Boolean value indicating the state of the multiline flag (m) used with a regular expression. Default is false. Read-only.                                  |
| **`lastIndex`**  | <code>number</code>  |                                                                                                                                                                      |

| Method      | Signature                                                                     | Description                                                                                                                   |
| ----------- | ----------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| **exec**    | (string: string) =&gt; <a href="#regexpexecarray">RegExpExecArray</a> \| null | Executes a search on a string using a regular expression pattern, and returns an array containing the results of that search. |
| **test**    | (string: string) =&gt; boolean                                                | Returns a Boolean value that indicates whether or not a pattern exists in a searched string.                                  |
| **compile** | () =&gt; this                                                                 |                                                                                                                               |


#### RegExpExecArray

| Prop        | Type                |
| ----------- | ------------------- |
| **`index`** | <code>number</code> |
| **`input`** | <code>string</code> |


#### Location

| Prop                   | Type                        |
| ---------------------- | --------------------------- |
| **`latitude`**         | <code>number</code>         |
| **`longitude`**        | <code>number</code>         |
| **`accuracy`**         | <code>number</code>         |
| **`altitude`**         | <code>number \| null</code> |
| **`altitudeAccuracy`** | <code>number \| null</code> |
| **`simulated`**        | <code>boolean</code>        |
| **`bearing`**          | <code>number \| null</code> |
| **`speed`**            | <code>number \| null</code> |
| **`time`**             | <code>number \| null</code> |


#### Object

Provides functionality common to all JavaScript objects.

| Prop              | Type                                          | Description                                                                                                                                |
| ----------------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| **`constructor`** | <code><a href="#function">Function</a></code> | The initial value of <a href="#object">Object</a>.prototype.constructor is the standard built-in <a href="#object">Object</a> constructor. |

| Method                   | Signature                                                 | Description                                                              |
| ------------------------ | --------------------------------------------------------- | ------------------------------------------------------------------------ |
| **toString**             | () =&gt; string                                           | Returns a string representation of an object.                            |
| **toLocaleString**       | () =&gt; string                                           | Returns a date converted to a string using the current locale.           |
| **valueOf**              | () =&gt; <a href="#object">Object</a>                     | Returns the primitive value of the specified object.                     |
| **hasOwnProperty**       | (v: <a href="#propertykey">PropertyKey</a>) =&gt; boolean | Determines whether an object has a property with the specified name.     |
| **isPrototypeOf**        | (v: <a href="#object">Object</a>) =&gt; boolean           | Determines whether an object exists in another object's prototype chain. |
| **propertyIsEnumerable** | (v: <a href="#propertykey">PropertyKey</a>) =&gt; boolean | Determines whether a specified property is enumerable.                   |


#### Function

Creates a new function.

| Prop            | Type                                          |
| --------------- | --------------------------------------------- |
| **`prototype`** | <code>any</code>                              |
| **`length`**    | <code>number</code>                           |
| **`arguments`** | <code>any</code>                              |
| **`caller`**    | <code><a href="#function">Function</a></code> |

| Method       | Signature                                                                            | Description                                                                                                                                                                                                              |
| ------------ | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **apply**    | (this: <a href="#function">Function</a>, thisArg: any, argArray?: any) =&gt; any     | Calls the function, substituting the specified object for the this value of the function, and the specified array for the arguments of the function.                                                                     |
| **call**     | (this: <a href="#function">Function</a>, thisArg: any, ...argArray: any[]) =&gt; any | Calls a method of an object, substituting another object for the current object.                                                                                                                                         |
| **bind**     | (this: <a href="#function">Function</a>, thisArg: any, ...argArray: any[]) =&gt; any | For a given function, creates a bound function that has the same body as the original function. The this object of the bound function is associated with the specified object, and has the specified initial parameters. |
| **toString** | () =&gt; string                                                                      | Returns a string representation of a function.                                                                                                                                                                           |


### Type Aliases


#### PropertyKey

<code>string | number | symbol</code>

</docgen-api>
