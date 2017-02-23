package com.badnewsbears.badnewscomics.xml;

public class NameFilter implements NodeFilter {
    private final String _name;

    public NameFilter(final String name) {
        _name = name;
    }

    @Override
    public boolean accept(final XmlNode node) {
        return _name.equalsIgnoreCase(node.getName());
    }
}
