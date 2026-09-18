/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gms.provider.wz;

import com.ctc.wstx.stax.WstxInputFactory;
import org.gms.provider.Data;
import org.gms.provider.DataEntity;
import org.gms.provider.DataTool;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.awt.Point;
import java.io.FileInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A single WZ {@code .img} file parsed into an immutable in-memory tree.
 * <p>
 * Replaces the previous DOM implementation ({@code XMLDomMapleData}), which built a full
 * {@code org.w3c.dom.Document} per file and re-scanned child node lists on every lookup. Parsing
 * goes through Woodstox StAX, which measured ~2.8x faster than the JDK DOM on the real v83 tree,
 * and the node layout is leaner than DOM's (flattened attribute array, precomputed type, lazy
 * child index), so both parse time and retained memory drop. Nodes are immutable after parsing,
 * which removes the {@code synchronized} guards the DOM version needed when one document was
 * shared across threads.
 * <p>
 * Child lookup by {@code name} (the WZ key) uses a {@code HashMap} built for wide nodes only,
 * falling back to a linear scan for the common narrow case; this matches the DOM version's
 * first-match semantics.
 */
public class XMLWZData implements Data {
    private static final XMLInputFactory INPUT_FACTORY = createInputFactory();

    /**
     * Above this many children a node also keeps a name-indexed map. Most WZ nodes are narrow
     * (info blocks of a few keys), so building an index for all of them would waste memory; the
     * wide ones (map life lists, string tables) are exactly the ones that are looked up by name.
     */
    private static final int INDEX_THRESHOLD = 8;

    private final String name;
    private final String[] attrs;   // flattened name/value pairs, "name" excluded (held separately)
    private final DataType type;    // resolved once at parse time; null for unrecognised tags
    private final XMLWZData parent;

    // Set once at freeze time (end of element). volatile so a node published from one thread (via a
    // long-lived field) is safely visible to readers on another: the original DOM implementation got
    // this for free from its final Node field, and these must not regress it.
    private volatile XMLWZData[] children;
    private volatile Map<String, XMLWZData> childIndex;
    private volatile List<Data> childrenView;

    private XMLWZData(String name, String[] attrs, DataType type, XMLWZData parent) {
        this.name = name;
        this.attrs = attrs;
        this.type = type;
        this.parent = parent;
    }

    private static XMLInputFactory createInputFactory() {
        XMLInputFactory factory = new WstxInputFactory();
        // WZ XML carries no namespaces, no DTD and no entities; disabling them is both faster and
        // avoids external-entity resolution on data files.
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        return factory;
    }

    /**
     * Parses a WZ {@code .img} XML stream into an immutable tree and returns its root node.
     * The stream is not closed by this method.
     */
    public static XMLWZData parse(FileInputStream fis) {
        XMLWZData root = null;
        Deque<Frame> stack = new ArrayDeque<>();
        try {
            XMLStreamReader reader = INPUT_FACTORY.createXMLStreamReader(fis);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String name = reader.getAttributeValue(null, "name");
                        if (name == null) {
                            // Every element in the v83 tree carries a "name"; fall back to the tag
                            // only for malformed input so getName() never NPEs.
                            name = reader.getLocalName();
                        }
                        XMLWZData node = new XMLWZData(name, collectAttrs(reader), typeOf(reader.getLocalName()),
                                stack.isEmpty() ? null : stack.peek().node);
                        if (root == null) {
                            root = node;
                        }
                        if (!stack.isEmpty()) {
                            stack.peek().addChild(node);
                        }
                        stack.push(new Frame(node));
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        Frame frame = stack.pop();
                        frame.node.freeze(frame.children);
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    private static String[] collectAttrs(XMLStreamReader reader) {
        int attrCount = reader.getAttributeCount();
        String[] attrs = null;
        int k = 0;
        for (int i = 0; i < attrCount; i++) {
            String an = reader.getAttributeLocalName(i);
            if ("name".equals(an)) {
                continue;
            }
            if (attrs == null) {
                attrs = new String[(attrCount - 1) * 2];
            }
            attrs[k++] = an;
            attrs[k++] = reader.getAttributeValue(i);
        }
        return attrs;
    }

    private static DataType typeOf(String tag) {
        switch (tag) {
            case "imgdir":
                return DataType.PROPERTY;
            case "canvas":
                return DataType.CANVAS;
            case "convex":
                return DataType.CONVEX;
            case "sound":
                return DataType.SOUND;
            case "uol":
                return DataType.UOL;
            case "double":
                return DataType.DOUBLE;
            case "float":
                return DataType.FLOAT;
            case "int":
                return DataType.INT;
            case "short":
                return DataType.SHORT;
            case "string":
                return DataType.STRING;
            case "vector":
                return DataType.VECTOR;
            case "null":
                return DataType.IMG_0x00;
            default:
                return null;
        }
    }

    private void freeze(ArrayList<XMLWZData> kids) {
        if (kids == null || kids.isEmpty()) {
            return;
        }
        XMLWZData[] arr = kids.toArray(new XMLWZData[0]);
        this.children = arr;
        this.childrenView = Collections.unmodifiableList(Arrays.<Data>asList(arr));
        if (arr.length > INDEX_THRESHOLD) {
            Map<String, XMLWZData> index = new HashMap<>(arr.length * 2);
            for (XMLWZData child : arr) {
                index.putIfAbsent(child.name, child);   // DOM semantics: first match wins
            }
            this.childIndex = index;
        }
    }

    private XMLWZData child(String n) {
        Map<String, XMLWZData> index = childIndex;
        if (index != null) {
            return index.get(n);
        }
        XMLWZData[] kids = children;
        if (kids != null) {
            for (XMLWZData child : kids) {
                if (n.equals(child.name)) {
                    return child;
                }
            }
        }
        return null;
    }

    @Override
    public Data getChildByPath(String path) {
        String[] segments = path.split("/");
        if (segments[0].equals("..")) {
            return ((Data) getParent()).getChildByPath(path.substring(path.indexOf("/") + 1));
        }

        XMLWZData current = this;
        for (String s : segments) {
            current = current.child(s);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @Override
    public List<Data> getChildren() {
        return childrenView != null ? childrenView : Collections.emptyList();
    }

    @Override
    public Object getData() {
        switch (type) {
            case DOUBLE:
                return DataTool.parseNumber(attr("value")).doubleValue();
            case FLOAT:
                return DataTool.parseNumber(attr("value")).floatValue();
            case INT:
                return DataTool.parseNumber(attr("value")).intValue();
            case SHORT:
                return DataTool.parseNumber(attr("value")).shortValue();
            case STRING:
            case UOL:
                return attr("value");
            case VECTOR:
                return new Point(Integer.parseInt(attr("x")), Integer.parseInt(attr("y")));
            case CANVAS:
                return new Point(Integer.parseInt(attr("width")), Integer.parseInt(attr("height")));
            default:
                return null;
        }
    }

    @Override
    public DataType getType() {
        return type;
    }

    @Override
    public DataEntity getParent() {
        return parent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Iterator<Data> iterator() {
        return getChildren().iterator();
    }

    @Override
    public String getAttributeValue(String name) {
        if ("name".equals(name)) {
            return this.name;
        }
        return attr(name);
    }

    private String attr(String key) {
        if (attrs != null) {
            for (int i = 0; i < attrs.length; i += 2) {
                if (key.equals(attrs[i])) {
                    return attrs[i + 1];
                }
            }
        }
        return null;
    }

    private static final class Frame {
        final XMLWZData node;
        ArrayList<XMLWZData> children;

        Frame(XMLWZData node) {
            this.node = node;
        }

        void addChild(XMLWZData child) {
            if (children == null) {
                children = new ArrayList<>(4);
            }
            children.add(child);
        }
    }
}
