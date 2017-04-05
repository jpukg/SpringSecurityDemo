package com.vaadin.data.util.sqlcontainer.demo.addressbook.ui;

import com.vaadin.ui.SplitPanel;

@SuppressWarnings("serial")
public class ListView extends SplitPanel {
    public ListView(PersonList personList, PersonForm personForm) {
        addStyleName("view");
        setFirstComponent(personList);
        setSecondComponent(personForm);
        setSplitPosition(40);
    }
}