package com.badnewsbears.badnewscomics.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Composite XML tree structure. Supports reading & writing to file.
 */
@SuppressWarnings("unused")
public class XmlNode {

    private XmlNode parent;
    private boolean showAttributes;
    private String name, value;
    private HashMap<String, String> attMap;
    private ArrayList<XmlNode> children;

    public XmlNode(XmlNode parent, String name, Attributes attributes,
            String value, boolean showAttributes) {
        this.parent = parent;
        this.name = name;
        this.value = value;
        this.showAttributes = showAttributes;

        if (attributes != null && attributes.getLength() > 0) {
            this.attMap = new HashMap<String, String>();
            for (int i = 0; i < attributes.getLength(); i++) {
                attMap.put(attributes.getQName(i), attributes.getValue(i));
            }
        }
    }

    public XmlNode(String name, Attributes attributes, boolean showAttributes) {
        this.name = name;
        this.showAttributes = showAttributes;

        if (attributes != null && attributes.getLength() > 0) {
            this.attMap = new HashMap<String, String>();
            for (int i = 0; i < attributes.getLength(); i++) {
                attMap.put(attributes.getQName(i), attributes.getValue(i));
            }
        }
    }

    public XmlNode(String name, Attributes attributes) {
        this.name = name;

        if (attributes != null && attributes.getLength() > 0) {
            this.attMap = new HashMap<String, String>();
            for (int i = 0; i < attributes.getLength(); i++) {
                attMap.put(attributes.getQName(i), attributes.getValue(i));
            }
        }
    }

    public XmlNode(String name) {
        this.name = name;
    }

    public static XmlNode parse(InputStream in) throws IOException,
            ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();

        final XmlNode root = new XmlNode("Root", null);

        DefaultHandler handler = new DefaultHandler() {

            XmlNode node; // current node
            XmlNode parent;

            @Override
            public void startDocument() {
                parent = root;
            }

            @Override
            public void endDocument() { }

            @Override
            public void startElement(String uri, String localName,
                    String qName, Attributes attributes) throws SAXException {
                parent.add(node = new XmlNode(parent, qName, attributes, null, true));
                parent = node;
            }

            @Override
            public void endElement(String uri, String localName, String qName)
                    throws SAXException {
                parent = node.getParent();
                node = parent;
            }

            @Override
            public void characters(char ch[], int start, int length)
                    throws SAXException {
                if (length > 0) {
                    node.setValue(new String(ch, start, length));
                }
            }
        };

        saxParser.parse(in, handler);

        return root.getNext();
    }

    public void add(XmlNode node) {
        if (children == null)
            children = new ArrayList<XmlNode>();

        children.add(node);
    }

    public void remove(XmlNode node) {
        if (children != null)
            children.remove(node);
    }

    public XmlNode getParent() {
        return parent;
    }

    public int getChildCount() {
        return children == null ? 0 : children.size();
    }

    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }

    public void visitChildren(NodeVisitor visitor) {
        if (children != null) {
            for (XmlNode node : children)
                node.accept(visitor);
        }
    }

    public XmlNode findFirst(NodeFilter filter) {
        for (int i = 0; i < children.size(); i++) {
            final XmlNode child = children.get(i);

            if (filter.accept(child)) {
                return child;
            }
        }

        return null;
    }

    public XmlNode[] find(NodeFilter filter) {
        final ArrayList<XmlNode> ret = new ArrayList<XmlNode>();
        find(filter, ret, false);
        return ret.toArray(new XmlNode[ret.size()]);
    }

    public XmlNode[] findAll(NodeFilter filter) {
        final ArrayList<XmlNode> ret = new ArrayList<XmlNode>();
        find(filter, ret, true);
        return ret.toArray(new XmlNode[ret.size()]);
    }

    protected void find(NodeFilter filter, List<XmlNode> found,
                        boolean recurse) {
        for (int i = 0; i < getChildCount(); i++) {
            final XmlNode child = children.get(i);

            if (filter.accept(child)) {
                found.add(child);
            }

            if (recurse) {
                child.find(filter, found, true);
            }
        }
    }

    public int count(NodeFilter filter) {
        return count(filter, false);
    }

    public int countAll(NodeFilter filter) {
        return count(filter, true);
    }

    protected int count(NodeFilter filter, boolean recurse) {
        int ret = 0;
        for (int i = 0; i < children.size(); i++) {
            final XmlNode child = children.get(i);

            if (filter.accept(child)) {
                ret++;
            }

            if (recurse) {
                ret += child.count(filter, true);
            }
        }

        return ret;
    }

    public XmlNode findChild(String name) {
        XmlNode ret;
        if (children != null && children.size() > 0)
            for (int i = 0; i < this.children.size(); i++) {
                ret = children.get(i);
                if (ret.getName().equalsIgnoreCase(name))
                    return ret;
            }

        return null;
    }

    public XmlNode[] findChildren(String name) {
        return find(new NameFilter(name));
    }

    public String getChildValue(String name) {
        XmlNode child;
        for (int i = 0; i < this.getChildCount(); i++)
            if ((child = children.get(i)).getName()
                    .equalsIgnoreCase(name)) {
                return child.getValue();
            }

        return null;
    }

    public XmlNode getNext() {
        if (children != null)
            return children.get(0);
        else
            return parent.getNext(this);
    }

    public XmlNode getNext(XmlNode node) {
        int n = children.indexOf(node) + 1;
        if (n < children.size()) {
            return children.get(n);
        } else if (parent != null) {
            return parent.getNext(this);
        } else {
            return null;
        }
    }

    public String getName() {
        return name;
    }

    public boolean getShowAttributes() {
        return showAttributes;
    }

    public void setShowAttributes(boolean flag) {
        showAttributes = flag;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getAttributeValue(String name) {
        return attMap == null ? null : attMap.get(name);
    }

    public void setAttributeValue(String name, String value) {
        if (attMap == null)
            attMap = new HashMap<String, String>();

        attMap.put(name, value);
    }

    public void setAttributeValue(String name, Object value) {
        if (attMap == null)
            attMap = new HashMap<String, String>();

        attMap.put(name, value.toString());
    }

	public void dump(PrintStream out, boolean pretty) {
		dump(out, pretty, "");
	}

    public void dump(PrintStream out, boolean pretty, String prefix) {
		final String childPrefix = pretty ? prefix + "    " : prefix;

		if (pretty) { out.print(prefix); }

        out.print('<');
        out.print(name);

        if (attMap != null) {
            for (Entry<String, String> entry : attMap.entrySet()) {
                out.print(' ');
                out.print(entry.getKey());
                out.print("=\"");
                out.print(entry.getValue());
                out.print("\"");
            }
        }

        out.print('>');

		if (pretty) { out.println(); }

		if (value != null) {
			if (pretty) { out.print(childPrefix); }
			out.print(value);
			if (pretty) { out.println(); }
		}

        for (int i = 0; i < getChildCount(); i++) {
            children.get(i).dump(out, pretty, childPrefix);
        }

		if (pretty) { out.print(prefix); }

        out.print("</");
        out.print(name);
        out.print('>');

		if (pretty) { out.println(); }
    }

    @Override
    public String toString() {
        String ret = name;

        /*
         * if (showAttributes && attributes != null && attributes.getLength() >
         * 0) { int len = attributes.getLength(); ret += " ( "; for (int i = 0;
         * i < len; i++) { ret += attributes.getLocalName(i) + " = " +
         * attributes.getValue(i); if (i + 1 < len) ret += ", "; } ret += " ) ";
         * }
         */

        return value == null ? ret : ret + " Value='" + value + "'";
    }
}
