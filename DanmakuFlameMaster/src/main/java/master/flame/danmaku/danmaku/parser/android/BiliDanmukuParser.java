/*
 * Copyright (C) 2013 Chen Hui <calmer91@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package master.flame.danmaku.danmaku.parser.android;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.IOException;
import java.util.Locale;

import master.flame.danmaku.danmaku.model.AlphaValue;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.Duration;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.DanmakuFactory;

public class BiliDanmukuParser extends BaseDanmakuParser {

    static {
        System.setProperty("org.xml.sax.driver", "org.xmlpull.v1.sax2.Driver");
    }

    private float mDispScaleX;
    private float mDispScaleY;

    @Override
    public Danmakus parse() {

        if (mDataSource != null) {
            AndroidFileSource source = (AndroidFileSource) mDataSource;
            try {
                XMLReader xmlReader = XMLReaderFactory.createXMLReader();
                XmlContentHandler contentHandler = new XmlContentHandler();
                xmlReader.setContentHandler(contentHandler);
                xmlReader.parse(new InputSource(source.data()));
                return contentHandler.getResult();
            } catch (SAXException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        return null;
    }

    public class XmlContentHandler extends DefaultHandler {

        private static final String TRUE_STRING = "true";

        public Danmakus result = null;

        public BaseDanmaku item = null;

        public boolean completed = false;

        public int index = 0;

        public Danmakus getResult() {
            return result;
        }

        @Override
        public void startDocument() throws SAXException {
            result = new Danmakus();
        }

        @Override
        public void endDocument() throws SAXException {
            completed = true;
        }

        private boolean isDanmakuTag(String localName, String qName) {
            return localName != null && localName.length() != 0
                    ? "d".equalsIgnoreCase(localName) : "d".equalsIgnoreCase(qName);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if (!isDanmakuTag(localName, qName)) {
                return;
            }
            String pValue = attributes.getValue("p");
            if (pValue == null) {
                return;
            }
            int comma1 = pValue.indexOf(',');
            int comma2 = comma1 < 0 ? -1 : pValue.indexOf(',', comma1 + 1);
            int comma3 = comma2 < 0 ? -1 : pValue.indexOf(',', comma2 + 1);
            if (comma3 < 0) {
                return;
            }
            int comma4 = pValue.indexOf(',', comma3 + 1);
            item = null;
            try {
                long time = (long) (Float.parseFloat(pValue.substring(0, comma1)) * 1000);
                int type = Integer.parseInt(pValue.substring(comma1 + 1, comma2));
                float textSize = Float.parseFloat(pValue.substring(comma2 + 1, comma3));
                int color = Integer.parseInt(comma4 < 0
                        ? pValue.substring(comma3 + 1)
                        : pValue.substring(comma3 + 1, comma4)) | 0xFF000000;
                item = DanmakuFactory.createDanmaku(type, mDisp);
                if (item != null) {
                    item.time = time;
                    item.textSize = textSize * (mDispDensity - 0.6f);
                    item.textColor = color;
                    item.textShadowColor = color <= Color.BLACK ? Color.WHITE : Color.BLACK;
                }
            } catch (NumberFormatException e) {
                item = null;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (item != null) {
                if (item.duration != null) {
                    String tagName = localName.length() != 0 ? localName : qName;
                    if (tagName.equalsIgnoreCase("d")) {
                        item.setTimer(mTimer);
                        result.addItem(item);
                    }
                }
                item = null;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (item != null) {
                DanmakuFactory.fillText(item, decodeXmlString(new String(ch, start, length)));
                item.index = index++;

                if (item.getType() != BaseDanmaku.TYPE_SPECIAL) {
                    return;
                }
                String text = item.text == null ? "" : item.text.toString().trim();
                if (text.startsWith("[")
                        && text.endsWith("]")) {
                    String[] textArr = null;
                    try {
                        JSONArray jsonArray = new JSONArray(text);
                        textArr = new String[jsonArray.length()];
                        for (int i = 0; i < textArr.length; i++) {
                            textArr[i] = jsonArray.getString(i);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    if (textArr == null || textArr.length < 5) {
                        item = null;
                        return;
                    }
                    item.text = textArr[4];
                    float beginX = safeParseFloat(textArr[0]);
                    float beginY = safeParseFloat(textArr[1]);
                    float endX = beginX;
                    float endY = beginY;
                    String[] alphaArr = textArr[2].split("-");
                    int beginAlpha = (int) (AlphaValue.MAX * safeParseFloat(alphaArr[0]));
                    int endAlpha = beginAlpha;
                    if (alphaArr.length > 1) {
                        endAlpha = (int) (AlphaValue.MAX * safeParseFloat(alphaArr[1]));
                    }
                    long alphaDuraion = (long) (safeParseFloat(textArr[3]) * 1000);
                    long translationDuration = alphaDuraion;
                    long translationStartDelay = 0;
                    float rotateY = 0, rotateZ = 0;
                    if (textArr.length >= 7) {
                        rotateZ = safeParseFloat(textArr[5]);
                        rotateY = safeParseFloat(textArr[6]);
                    }
                    if (textArr.length >= 11) {
                        endX = safeParseFloat(textArr[7]);
                        endY = safeParseFloat(textArr[8]);
                        if (!"".equals(textArr[9])) {
                            translationDuration = Integer.parseInt(textArr[9]);
                        }
                        if (!"".equals(textArr[10])) {
                            translationStartDelay = (long) (safeParseFloat(textArr[10]));
                        }
                    }
                    item.duration = new Duration(alphaDuraion);
                    item.rotationZ = rotateZ;
                    item.rotationY = rotateY;
                    DanmakuFactory.fillTranslationData(item, beginX,
                            beginY, endX, endY, translationDuration, translationStartDelay, mDispScaleX, mDispScaleY);
                    DanmakuFactory.fillAlphaData(item, beginAlpha, endAlpha, alphaDuraion);

                    if (textArr.length >= 12) {
                        if (textArr[11] != null && TRUE_STRING.equals(textArr[11])) {
                            item.textShadowColor = Color.TRANSPARENT;
                        }
                    }
                    if (textArr.length >= 13) {
                        //TODO 字体 textArr[12]
                    }
                    if (textArr.length >= 14) {
                        //TODO 是否有加速
                    }
                    if (textArr.length >= 15) {
                        if (!"".equals(textArr[14])) {
                            String motionPathString = textArr[14].substring(1);
                            String[] pointStrArray = motionPathString.split("L");
                            if (pointStrArray != null && pointStrArray.length > 0) {
                                float[][] points = new float[pointStrArray.length][2];
                                for (int i = 0; i < pointStrArray.length; i++) {
                                    String[] pointArray = pointStrArray[i].split(",");
                                    points[i][0] = Float.parseFloat(pointArray[0]);
                                    points[i][1] = Float.parseFloat(pointArray[1]);
                                }
                                DanmakuFactory.fillLinePathData(item, points, mDispScaleX,
                                        mDispScaleY);
                            }
                        }
                    }
                }

            }
        }

        private String decodeXmlString(String title) {
            if (title.contains("&amp;")) {
                title = title.replace("&amp;", "&");
            }
            if (title.contains("&quot;")) {
                title = title.replace("&quot;", "\"");
            }
            if (title.contains("&gt;")) {
                title = title.replace("&gt;", ">");
            }
            if (title.contains("&lt;")) {
                title = title.replace("&lt;", "<");
            }
            return title;
        }

    }

    @Override
    public BaseDanmakuParser setDisplayer(IDisplayer disp) {
        super.setDisplayer(disp);
        mDispScaleX = mDispWidth / DanmakuFactory.BILI_PLAYER_WIDTH;
        mDispScaleY = mDispHeight / DanmakuFactory.BILI_PLAYER_HEIGHT;
        return this;
    }

    private static float safeParseFloat(String s) {
        if (s == null || s.length() == 0) return 0f;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }
}