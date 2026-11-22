using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Threading;
using System.Windows;
using System.IO.IsolatedStorage;
using System.Text;

namespace BiliClassic.Api
{
    public class Json_Bili7
    {
        // ============ 新增：对外一次性解析入口 ============
        public static object Parse(string json)
        {
            if (string.IsNullOrEmpty(json))
                return null;
            return ParseJson(json.Trim());
        }

        // ============ 新增：从已解析对象树按路径取值 ============
        public static string GetValue(object root, string json_path)
        {
            try
            {
                if (root == null || string.IsNullOrEmpty(json_path))
                    return null;

                string[] segments = json_path.Split('.');
                object current = root;

                foreach (string segment in segments)
                {
                    if (current == null)
                        return null;

                    if (segment.Contains("[") && segment.Contains("]"))
                    {
                        int startBracket = segment.IndexOf('[');
                        int endBracket = segment.IndexOf(']');
                        string propName = segment.Substring(0, startBracket);
                        string indexStr = segment.Substring(startBracket + 1, endBracket - startBracket - 1);
                        int index = Convert.ToInt32(indexStr);

                        if (!string.IsNullOrEmpty(propName))
                        {
                            if (!(current is Dictionary<string, object>))
                                return null;

                            var dict = (Dictionary<string, object>)current;
                            if (!dict.ContainsKey(propName))
                                return null;

                            current = dict[propName];
                        }

                        if (!(current is List<object>))
                            return null;

                        var list = (List<object>)current;
                        if (index < 0 || index >= list.Count)
                            return null;

                        current = list[index];
                    }
                    else
                    {
                        if (!(current is Dictionary<string, object>))
                            return null;

                        var dict = (Dictionary<string, object>)current;
                        if (!dict.ContainsKey(segment))
                            return null;

                        current = dict[segment];
                    }
                }

                if (current == null)
                    return null;

                if (current is string || current is int || current is long ||
                    current is double || current is bool || current is decimal)
                {
                    return current.ToString();
                }

                return SerializeValue(current);
            }
            catch
            {
                return null;
            }
        }

        // ============ 原方法保留，内部改为 Parse + GetValue ============
        public static string Get_Json_Value(string json, string json_path)
        {
            try
            {
                object root = Parse(json);
                return GetValue(root, json_path);
            }
            catch
            {
                return null;
            }
        }


        private static object ParseJson(string json)
        {
            if (string.IsNullOrEmpty(json))
                return null;

            json = json.Trim();

            if (json.StartsWith("{"))
            {
                return ParseObject(json);
            }
            else if (json.StartsWith("["))
            {
                return ParseArray(json);
            }
            else
            {
                return ParseValue(json);
            }
        }

        private static Dictionary<string, object> ParseObject(string json)
        {
            var dict = new Dictionary<string, object>();
            json = json.Trim();

            if (json.StartsWith("{") && json.EndsWith("}"))
            {
                json = json.Substring(1, json.Length - 2).Trim();
            }
            else
            {
                return null;
            }

            if (string.IsNullOrEmpty(json))
                return dict;

            var pairs = SplitJson(json, ',');

            foreach (string pair in pairs)
            {
                if (string.IsNullOrEmpty(pair))
                    continue;

                int colonIndex = FindColonIndex(pair);
                if (colonIndex == -1)
                    continue;

                string key = pair.Substring(0, colonIndex).Trim();
                string value = pair.Substring(colonIndex + 1).Trim();

                if (key.StartsWith("\"") && key.EndsWith("\""))
                    key = key.Substring(1, key.Length - 2);
                else if (key.StartsWith("'") && key.EndsWith("'"))
                    key = key.Substring(1, key.Length - 2);

                object parsedValue = ParseJson(value);
                dict[key] = parsedValue;
            }

            return dict;
        }

        private static List<object> ParseArray(string json)
        {
            var list = new List<object>();
            json = json.Trim();

            if (json.StartsWith("[") && json.EndsWith("]"))
            {
                json = json.Substring(1, json.Length - 2).Trim();
            }
            else
            {
                return null;
            }

            if (string.IsNullOrEmpty(json))
                return list;

            var items = SplitJson(json, ',');

            foreach (string item in items)
            {
                if (string.IsNullOrEmpty(item))
                    continue;

                object parsedItem = ParseJson(item.Trim());
                list.Add(parsedItem);
            }

            return list;
        }

        private static object ParseValue(string json)
        {
            json = json.Trim();

            if (string.IsNullOrEmpty(json))
                return null;

            if (json.StartsWith("\"") && json.EndsWith("\""))
            {
                return json.Substring(1, json.Length - 2);
            }

            if (json.StartsWith("'") && json.EndsWith("'"))
            {
                return json.Substring(1, json.Length - 2);
            }

            if (json == "true")
                return true;
            if (json == "false")
                return false;
            if (json == "null")
                return null;

            // WP7兼容的数字解析
            try
            {
                // 尝试解析为整数
                if (json.IndexOf('.') == -1)
                {
                    long longValue = 0;
                    if (long.TryParse(json, out longValue))
                        return longValue;
                }
                else
                {
                    double doubleValue = 0;
                    if (double.TryParse(json, out doubleValue))
                        return doubleValue;
                }
            }
            catch
            {
                // 解析失败，返回字符串
            }

            return json;
        }

        private static string SerializeValue(object value)
        {
            if (value == null)
                return "null";

            if (value is string)
                return "\"" + value + "\"";

            if (value is bool)
                return value.ToString().ToLower();

            if (value is int || value is long || value is double || value is decimal)
                return value.ToString();

            if (value is Dictionary<string, object>)
            {
                var dict = (Dictionary<string, object>)value;
                var sb = new StringBuilder();
                sb.Append("{");
                bool first = true;
                foreach (var kvp in dict)
                {
                    if (!first) sb.Append(",");
                    sb.Append("\"").Append(kvp.Key).Append("\":");
                    sb.Append(SerializeValue(kvp.Value));
                    first = false;
                }
                sb.Append("}");
                return sb.ToString();
            }

            if (value is List<object>)
            {
                var list = (List<object>)value;
                var sb = new StringBuilder();
                sb.Append("[");
                bool first = true;
                foreach (var item in list)
                {
                    if (!first) sb.Append(",");
                    sb.Append(SerializeValue(item));
                    first = false;
                }
                sb.Append("]");
                return sb.ToString();
            }

            return value.ToString();
        }

        private static List<string> SplitJson(string json, char separator)
        {
            var result = new List<string>();
            int depth = 0;
            int start = 0;
            bool inString = false;
            char stringChar = '\0';

            for (int i = 0; i < json.Length; i++)
            {
                char c = json[i];

                if (!inString && (c == '"' || c == '\''))
                {
                    inString = true;
                    stringChar = c;
                }
                else if (inString && c == stringChar && (i == 0 || json[i - 1] != '\\'))
                {
                    inString = false;
                }

                if (inString)
                    continue;

                if (c == '{' || c == '[')
                    depth++;
                else if (c == '}' || c == ']')
                    depth--;
                else if (c == separator && depth == 0)
                {
                    string item = json.Substring(start, i - start).Trim();
                    if (!string.IsNullOrEmpty(item))
                        result.Add(item);
                    start = i + 1;
                }
            }

            string lastItem = json.Substring(start).Trim();
            if (!string.IsNullOrEmpty(lastItem))
                result.Add(lastItem);

            return result;
        }

        private static int FindColonIndex(string json)
        {
            bool inString = false;
            char stringChar = '\0';
            int depth = 0;

            for (int i = 0; i < json.Length; i++)
            {
                char c = json[i];

                if (!inString && (c == '"' || c == '\''))
                {
                    inString = true;
                    stringChar = c;
                }
                else if (inString && c == stringChar && (i == 0 || json[i - 1] != '\\'))
                {
                    inString = false;
                }

                if (inString)
                    continue;

                if (c == '{' || c == '[')
                    depth++;
                else if (c == '}' || c == ']')
                    depth--;
                else if (c == ':' && depth == 0)
                    return i;
            }

            return -1;
        }
    }

    public sealed class JsonValue
    {
        private const int KindNull = 0;
        private const int KindBool = 1;
        private const int KindNumber = 2;
        private const int KindString = 3;
        private const int KindArray = 4;
        private const int KindObject = 5;

        private readonly int _kind;
        private readonly bool _bool;
        private readonly double _number;
        private readonly string _string;
        private readonly List<JsonValue> _array;
        private readonly Dictionary<string, JsonValue> _object;

        public static readonly JsonValue Null = new JsonValue();

        private JsonValue()
        {
            _kind = KindNull;
        }

        private JsonValue(bool value)
        {
            _kind = KindBool;
            _bool = value;
        }

        private JsonValue(double value)
        {
            _kind = KindNumber;
            _number = value;
        }

        private JsonValue(string value)
        {
            _kind = KindString;
            _string = value;
        }

        private JsonValue(List<JsonValue> value)
        {
            _kind = KindArray;
            _array = value;
        }

        private JsonValue(Dictionary<string, JsonValue> value)
        {
            _kind = KindObject;
            _object = value;
        }

        public bool IsNull
        {
            get { return _kind == KindNull; }
        }

        public bool IsObject
        {
            get { return _kind == KindObject; }
        }

        public bool IsArray
        {
            get { return _kind == KindArray; }
        }

        public int Count
        {
            get
            {
                if (_kind == KindArray)
                {
                    return _array.Count;
                }
                if (_kind == KindObject)
                {
                    return _object.Count;
                }
                return 0;
            }
        }

        public JsonValue this[int index]
        {
            get
            {
                if (_kind == KindArray && index >= 0 && index < _array.Count)
                {
                    return _array[index];
                }
                return Null;
            }
        }

        public JsonValue this[string key]
        {
            get
            {
                JsonValue value;
                if (_kind == KindObject && key != null && _object.TryGetValue(key, out value))
                {
                    return value;
                }
                return Null;
            }
        }

        public string AsString()
        {
            if (_kind == KindString)
            {
                return _string;
            }
            if (_kind == KindNumber)
            {
                return _number.ToString(CultureInfo.InvariantCulture);
            }
            if (_kind == KindBool)
            {
                return _bool ? "true" : "false";
            }
            return "";
        }

        public long AsLong()
        {
            long value;
            return long.TryParse(AsString(), out value) ? value : 0;
        }

        public int AsInt()
        {
            return (int)AsLong();
        }

        public bool AsBool()
        {
            if (_kind == KindBool)
            {
                return _bool;
            }
            string text = AsString();
            return text == "1" || string.Equals(text, "true", StringComparison.OrdinalIgnoreCase);
        }

        public static JsonValue Parse(string text)
        {
            if (string.IsNullOrEmpty(text))
            {
                return Null;
            }
            int pos = 0;
            JsonValue value = ParseValue(text, ref pos);
            return value == null ? Null : value;
        }

        private static JsonValue ParseValue(string text, ref int pos)
        {
            Skip(text, ref pos);
            if (pos >= text.Length)
            {
                return Null;
            }

            char c = text[pos];
            if (c == '{')
            {
                return ParseObject(text, ref pos);
            }
            if (c == '[')
            {
                return ParseArray(text, ref pos);
            }
            if (c == '"')
            {
                return new JsonValue(ParseString(text, ref pos));
            }
            if (c == 't')
            {
                pos += 4;
                return new JsonValue(true);
            }
            if (c == 'f')
            {
                pos += 5;
                return new JsonValue(false);
            }
            if (c == 'n')
            {
                pos += 4;
                return Null;
            }
            return new JsonValue(ParseNumber(text, ref pos));
        }

        private static JsonValue ParseObject(string text, ref int pos)
        {
            Dictionary<string, JsonValue> map = new Dictionary<string, JsonValue>();
            pos++;
            Skip(text, ref pos);
            if (pos < text.Length && text[pos] == '}')
            {
                pos++;
                return new JsonValue(map);
            }

            while (pos < text.Length)
            {
                Skip(text, ref pos);
                if (pos >= text.Length || text[pos] != '"')
                {
                    break;
                }

                string key = ParseString(text, ref pos);
                Skip(text, ref pos);
                if (pos < text.Length && text[pos] == ':')
                {
                    pos++;
                }

                map[key] = ParseValue(text, ref pos);
                Skip(text, ref pos);

                if (pos < text.Length && text[pos] == ',')
                {
                    pos++;
                    continue;
                }
                if (pos < text.Length && text[pos] == '}')
                {
                    pos++;
                }
                break;
            }
            return new JsonValue(map);
        }

        private static JsonValue ParseArray(string text, ref int pos)
        {
            List<JsonValue> list = new List<JsonValue>();
            pos++;
            Skip(text, ref pos);
            if (pos < text.Length && text[pos] == ']')
            {
                pos++;
                return new JsonValue(list);
            }

            while (pos < text.Length)
            {
                list.Add(ParseValue(text, ref pos));
                Skip(text, ref pos);

                if (pos < text.Length && text[pos] == ',')
                {
                    pos++;
                    continue;
                }
                if (pos < text.Length && text[pos] == ']')
                {
                    pos++;
                }
                break;
            }
            return new JsonValue(list);
        }

        private static string ParseString(string text, ref int pos)
        {
            pos++;
            int start = pos;
            bool escape = false;
            while (pos < text.Length)
            {
                char c = text[pos];
                if (escape)
                {
                    escape = false;
                    pos++;
                    continue;
                }
                if (c == '\\')
                {
                    escape = true;
                    pos++;
                    continue;
                }
                if (c == '"')
                {
                    break;
                }
                pos++;
            }

            string raw = text.Substring(start, pos - start);
            if (pos < text.Length && text[pos] == '"')
            {
                pos++;
            }
            return JsonText.Unescape(raw);
        }

        private static double ParseNumber(string text, ref int pos)
        {
            int start = pos;
            while (pos < text.Length)
            {
                char c = text[pos];
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.'
                    || c == 'e' || c == 'E')
                {
                    pos++;
                    continue;
                }
                break;
            }

            double value;
            double.TryParse(text.Substring(start, pos - start),
                NumberStyles.Float, CultureInfo.InvariantCulture, out value);
            return value;
        }

        private static void Skip(string text, ref int pos)
        {
            while (pos < text.Length)
            {
                char c = text[pos];
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n')
                {
                    pos++;
                    continue;
                }
                break;
            }
        }
    }
}
