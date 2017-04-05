/*
 * Copyright (c) 2010 The original author(s)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.peholmst.springsecuritydemo.ui;

import java.util.Locale;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Scope;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.github.peholmst.springsecuritydemo.VersionInfo;
import com.github.peholmst.springsecuritydemo.services.CategoryService;
import com.vaadin.Application;
import com.vaadin.data.Container.Filter;
import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.filter.Compare.Equal;
import com.vaadin.data.util.filter.Like;
import com.vaadin.data.util.filter.Or;
import com.vaadin.data.util.sqlcontainer.SQLContainer;
import com.vaadin.data.util.sqlcontainer.demo.addressbook.data.DatabaseHelper;
import com.vaadin.data.util.sqlcontainer.demo.addressbook.data.SearchFilter;
import com.vaadin.data.util.sqlcontainer.demo.addressbook.ui.HelpWindow;
import com.vaadin.data.util.sqlcontainer.demo.addressbook.ui.ListView;
import com.vaadin.data.util.sqlcontainer.demo.addressbook.ui.NavigationTree;
import com.vaadin.data.util.sqlcontainer.demo.addressbook.ui.PersonForm;
import com.vaadin.data.util.sqlcontainer.demo.addressbook.ui.PersonList;
import com.vaadin.data.util.sqlcontainer.demo.addressbook.ui.SearchView;
import com.vaadin.data.util.sqlcontainer.demo.addressbook.ui.SharingOptions;
import com.vaadin.data.util.sqlcontainer.query.QueryDelegate;
import com.vaadin.data.util.sqlcontainer.query.QueryDelegate.RowIdChangeEvent;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.event.ItemClickEvent.ItemClickListener;
import com.vaadin.service.ApplicationContext.TransactionListener;
import com.vaadin.terminal.ThemeResource;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.Component.Event;
import com.vaadin.ui.Embedded;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.HorizontalSplitPanel;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.Window.Notification;

/**
 * Main Vaadin application class for this demo. When first initialized, the
 * application displays an instance of {@link LoginView} as its main window.
 * Once a user has logged in successfully, the main window is replaced with an
 * instance of {@link MainView}, which remains the main window until the
 * application is closed.
 * <p>
 * This application class has been annotated with Spring application context
 * annotations. Its scope has been set to "prototype", meaning that a new
 * instance of the class will be returned every time the bean is accessed in the
 * Spring application context.
 * <p>
 * This class contains a lot of logging entries, the purpose of which is to make
 * it possible to follow what happens under the hood at different stages of the
 * application lifecycle.
 * 
 * @author Petter Holmström
 */
@org.springframework.stereotype.Component("applicationBean")
@Scope("prototype")
public class SpringSecurityDemoApp extends Application implements I18nProvider,
		TransactionListener, ClickListener, ValueChangeListener, ItemClickListener,
        QueryDelegate.RowIdChangeListener {

	private static final long serialVersionUID = -1412284137848857188L;

	/**
	 * Apache Commons logger for logging stuff.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	@Resource
	private MessageSource messages;

	@Resource
	private AuthenticationManager authenticationManager;

	@Resource
	private CategoryService categoryService;

	private LoginView loginView;

	private MainView mainView;

	private static final Locale[] SUPPORTED_LOCALES = { Locale.US,
			new Locale("fi", "FI"), new Locale("sv", "SE") };

	private static final String[] LOCALE_NAMES = { "English", "Suomi",
			"Svenska" };

	
	
	 /* Helper class that creates the tables and SQLContainers. */
    private final DatabaseHelper dbHelp = new DatabaseHelper();
    private final NavigationTree tree = new NavigationTree(this);

    private final Button newContact = new Button("Add contact");
    private final Button search = new Button("Search");
    private final Button share = new Button("Share");
    private final Button help = new Button("Help");
    private final HorizontalSplitPanel horizontalSplit = new HorizontalSplitPanel();

    // Lazily created UI references
    private ListView listView = null;
    private SearchView searchView = null;
    private PersonList personList = null;
    private PersonForm personForm = null;
    private HelpWindow helpWindow = null;
    private SharingOptions sharingOptions = null;
	
	@Override
	public Locale getLocale() {
		/*
		 * Fetch the locale resolved by Spring in the application servlet
		 */
		return LocaleContextHolder.getLocale();
	}

	@Override
	public void setLocale(Locale locale) {
		LocaleContextHolder.setLocale(locale);
	}

	@SuppressWarnings("serial")
	@Override
	public void init() {
		if (logger.isDebugEnabled()) {
			logger.debug("Initializing application [" + this + "]");
		}
		
		dbHelp.getPersonContainer().addListener(this);
		
		// Register listener
		getContext().addTransactionListener(this);

		// Create the views
		loginView = new LoginView(this, authenticationManager);
		loginView.setSizeFull();

		setTheme("SpringSecurityDemo"); // We use a custom theme

		final Window loginWindow = new Window(getMessage("app.title",
			getVersion()), loginView);
		setMainWindow(loginWindow);

		loginView.addListener(new com.vaadin.ui.Component.Listener() {
			@Override
			public void componentEvent(Event event) {
				if (event instanceof LoginView.LoginEvent) {
					if (logger.isDebugEnabled()) {
						logger.debug("User logged on ["
								+ ((LoginView.LoginEvent) event)
									.getAuthentication() + "]");
					}
					/*
					 * A user has logged on, which means we can ditch the login
					 * view and open the main view instead. We also have to
					 * update the security context holder.
					 */
					setUser(((LoginView.LoginEvent) event).getAuthentication());
					SecurityContextHolder.getContext().setAuthentication(
						((LoginView.LoginEvent) event).getAuthentication());
					removeWindow(loginWindow);
					loginView = null;
					
					/*mainView = new MainView(SpringSecurityDemoApp.this,
						categoryService);
					mainView.setSizeFull();
					setMainWindow(new Window(getMessage("app.title",
						getVersion()), mainView));*/
					
					
					buildMainLayout();
					setMainComponent(getListView());
//					dbHelp.getPersonContainer().addListener(this);
				}
			}
		});
	}

	@Override
	@PreDestroy
	// In case the application is destroyed by the container
	public void close() {
		if (logger.isDebugEnabled()) {
			logger.debug("Closing application [" + this + "]");
		}
		// Clear the authentication property to log the user out
		setUser(null);
		// Also clear the security context
		SecurityContextHolder.clearContext();
		getContext().removeTransactionListener(this);
		super.close();
	}

	@Override
	protected void finalize() throws Throwable {
		if (logger.isDebugEnabled()) {
			/*
			 * This is included to make sure that closed applications get
			 * properly garbage collected.
			 */
			logger.debug("Garbage collecting application [" + this + "]");
		}
		super.finalize();
	}

	@Override
	public void transactionEnd(Application application, Object transactionData) {
		if (logger.isDebugEnabled()) {
			logger
				.debug("Transaction ended, removing authentication data from security context");
		}
		/*
		 * The purpose of this
		 */
		SecurityContextHolder.getContext().setAuthentication(null);
	}

	@Override
	public void transactionStart(Application application, Object transactionData) {
		if (logger.isDebugEnabled()) {
			logger
				.debug("Transaction started, setting authentication data of security context to ["
						+ application.getUser() + "]");
		}
		/*
		 * The security context holder uses the thread local pattern to store
		 * its authentication credentials. As requests may be handled by
		 * different threads, we have to update the security context holder in
		 * the beginning of each transaction.
		 */
		SecurityContextHolder.getContext().setAuthentication(
			(Authentication) application.getUser());
	}

	/**
	 * Gets the currently logged in user. If this value is <code>null</code>, no
	 * user has been logged in yet.
	 * 
	 * @return an {@link Authentication} instance.
	 */
	@Override
	public Authentication getUser() {
		return (Authentication) super.getUser();
	}

	@Override
	public String getVersion() {
		return VersionInfo.getApplicationVersion();
	}

	@Override
	public String getMessage(String code, Object... args)
			throws NoSuchMessageException {
		return messages.getMessage(code, args, getLocale());
	}

	@Override
	public Locale[] getSupportedLocales() {
		return SUPPORTED_LOCALES;
	}

	@Override
	public String getLocaleDisplayName(Locale locale) {
		for (int i = 0; i < SUPPORTED_LOCALES.length; i++) {
			if (locale.equals(SUPPORTED_LOCALES[i])) {
				return LOCALE_NAMES[i];
			}
		}
		return "Unsupported Locale";
	}
	
	/* START */
	
	private void buildMainLayout() {
        setMainWindow(new Window("Address Book + SQLContainer Demo application"));

        setTheme("contacts");

        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();

        layout.addComponent(createToolbar());
        layout.addComponent(horizontalSplit);
        layout.setExpandRatio(horizontalSplit, 1);

        horizontalSplit
                .setSplitPosition(200, HorizontalSplitPanel.UNITS_PIXELS);
        horizontalSplit.setFirstComponent(tree);

        getMainWindow().setContent(layout);
    }

    private HorizontalLayout createToolbar() {
        HorizontalLayout lo = new HorizontalLayout();
        lo.addComponent(newContact);
        lo.addComponent(search);
        lo.addComponent(share);
        lo.addComponent(help);

        search.addListener((ClickListener) this);
        share.addListener((ClickListener) this);
        help.addListener((ClickListener) this);
        newContact.addListener((ClickListener) this);

        search.setIcon(new ThemeResource("icons/32/folder-add.png"));
        share.setIcon(new ThemeResource("icons/32/users.png"));
        help.setIcon(new ThemeResource("icons/32/help.png"));
        newContact.setIcon(new ThemeResource("icons/32/document-add.png"));

        lo.setMargin(true);
        lo.setSpacing(true);

        lo.setStyleName("toolbar");

        lo.setWidth("100%");

        Embedded em = new Embedded("", new ThemeResource("images/logo.png"));
        lo.addComponent(em);
        lo.setComponentAlignment(em, Alignment.MIDDLE_RIGHT);
        lo.setExpandRatio(em, 1);

        return lo;
    }

    private void setMainComponent(Component c) {
        horizontalSplit.setSecondComponent(c);
    }

    /*
     * View getters exist so we can lazily generate the views, resulting in
     * faster application startup time.
     */
    private ListView getListView() {
        if (listView == null) {
            personList = new PersonList(this);
            personForm = new PersonForm(this);
            listView = new ListView(personList, personForm);
        }
        return listView;
    }

    private SearchView getSearchView() {
        if (searchView == null) {
            searchView = new SearchView(this);
        }
        return searchView;
    }

    private HelpWindow getHelpWindow() {
        if (helpWindow == null) {
            helpWindow = new HelpWindow();
        }
        return helpWindow;
    }

    private SharingOptions getSharingOptions() {
        if (sharingOptions == null) {
            sharingOptions = new SharingOptions();
        }
        return sharingOptions;
    }

    public void buttonClick(ClickEvent event) {
        final Button source = event.getButton();

        if (source == search) {
            showSearchView();
            tree.select(NavigationTree.SEARCH);
        } else if (source == help) {
            showHelpWindow();
        } else if (source == share) {
            showShareWindow();
        } else if (source == newContact) {
            addNewContact();
        }
    }

    private void showHelpWindow() {
        getMainWindow().addWindow(getHelpWindow());
    }

    private void showShareWindow() {
        getMainWindow().addWindow(getSharingOptions());
    }

    private void showListView() {
        setMainComponent(getListView());
        personList.fixVisibleAndSelectedItem();
    }

    private void showSearchView() {
        setMainComponent(getSearchView());
        personList.fixVisibleAndSelectedItem();
    }

    public void valueChange(ValueChangeEvent event) {
        Property property = event.getProperty();
        if (property == personList) {
            Item item = personList.getItem(personList.getValue());
            if (item != personForm.getItemDataSource()) {
                personForm.setItemDataSource(item);
            }
        }
    }

    public void itemClick(ItemClickEvent event) {
        if (event.getSource() == tree) {
            Object itemId = event.getItemId();
            if (itemId != null) {
                if (NavigationTree.SHOW_ALL.equals(itemId)) {
                    /* Clear all filters from person container */
                    getDbHelp().getPersonContainer()
                            .removeAllContainerFilters();
                    showListView();
                } else if (NavigationTree.SEARCH.equals(itemId)) {
                    showSearchView();
                } else if (itemId instanceof SearchFilter[]) {
                    search((SearchFilter[]) itemId);
                }
            }
        }
    }

    private void addNewContact() {
        showListView();
        tree.select(NavigationTree.SHOW_ALL);
        /* Clear all filters from person container */
        getDbHelp().getPersonContainer().removeAllContainerFilters();
        personForm.addContact();
    }

    public void search(SearchFilter... searchFilters) {
        if (searchFilters.length == 0) {
            return;
        }
        SQLContainer c = getDbHelp().getPersonContainer();

        /* Clear all filters from person container. */
        getDbHelp().getPersonContainer().removeAllContainerFilters();

        /* Build an array of filters */
        Filter[] filters = new Filter[searchFilters.length];
        int ix = 0;
        for (SearchFilter searchFilter : searchFilters) {
            if (Integer.class.equals(c.getType(searchFilter.getPropertyId()))) {
                try {
                    filters[ix] = new Equal(searchFilter.getPropertyId(),
                            Integer.parseInt(searchFilter.getTerm()));
                } catch (NumberFormatException nfe) {
                    getMainWindow().showNotification("Invalid search term!");
                    return;
                }
            } else {
                filters[ix] = new Like((String) searchFilter.getPropertyId(),
                        "%" + searchFilter.getTerm() + "%");
            }
            ix++;
        }
        /* Add the filter(s) to the person container. */
        c.addContainerFilter(new Or(filters));
        showListView();

        getMainWindow().showNotification(
                "Searched for:<br/> "
                        + searchFilters[0].getPropertyIdDisplayName() + " = *"
                        + searchFilters[0].getTermDisplayName()
                        + "*<br/>Found " + c.size() + " item(s).",
                Notification.TYPE_TRAY_NOTIFICATION);
    }

    public void saveSearch(SearchFilter... searchFilter) {
        tree.addItem(searchFilter);
        tree.setItemCaption(searchFilter, searchFilter[0].getSearchName());
        tree.setParent(searchFilter, NavigationTree.SEARCH);
        // mark the saved search as a leaf (cannot have children)
        tree.setChildrenAllowed(searchFilter, false);
        // make sure "Search" is expanded
        tree.expandItem(NavigationTree.SEARCH);
        // select the saved search
        tree.setValue(searchFilter);
    }

    public DatabaseHelper getDbHelp() {
        return dbHelp;
    }

    public void rowIdChange(RowIdChangeEvent event) {
        /* Select the added item and fix the table scroll position */
        personList.select(event.getNewRowId());
        personList.fixVisibleAndSelectedItem();
    }
}
