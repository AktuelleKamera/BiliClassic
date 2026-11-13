using System;
using System.Collections.Generic;
using System.Globalization;

namespace BiliClassic.Api
{
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
