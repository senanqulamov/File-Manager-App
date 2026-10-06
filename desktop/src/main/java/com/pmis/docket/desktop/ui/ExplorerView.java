package com.pmis.docket.desktop.ui;

import com.pmis.docket.desktop.AppConfig;
import com.pmis.docket.desktop.CheckoutStore;
import com.pmis.docket.desktop.api.ApiClient;
import com.pmis.docket.desktop.api.ApiException;
import com.pmis.docket.desktop.api.Model;
import com.pmis.docket.desktop.api.Model.NodeInfo;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * The main window: Home, My files, Company, Shared with me and (for IT) the Admin console.
 * Explorer-style browsing plus every document function: open in the Docket viewer or the desktop app,
 * download, upload (button or drag & drop), new folder, cut/copy/paste, rename, delete with undo,
 * check-out / check-in with versions, share, convert, sign, lock, stamp, properties with activity.
 */
public class ExplorerView {
    private enum Place { HOME, SHARED, FOLDER, ADMIN }

    private enum Sort { NAME, DATE, TYPE, SIZE }

    private record Loc(Place place, Long id) {
        String key() { return place == Place.FOLDER ? "n:" + id : place.name(); }
    }

    private final WindowFrame frame;
    private final ApiClient api;
    private final AppConfig config;
    private final Model.UserInfo user;
    private final Runnable onSignOut;

    private final BorderPane root = new BorderPane();
    private final VBox toolbars = new VBox(10);
    private final HBox body = new HBox(12);
    private final HBox crumbs = new HBox(2);
    private final TextField search = new TextField();
    private final VBox sidebar = new VBox(1);
    private final StackPane contentCard = new StackPane();
    private final VBox contentBox = new VBox(14);
    private final ScrollPane contentScroll = new ScrollPane(contentBox);
    private final VBox previewPane = new VBox(12);
    private final StackPane previewCard = new StackPane(previewPane);
    private final Label dropHint = new Label("Drop files to upload");
    private final Label statusCount = new Label();
    private final Label statusSel = new Label();
    private final Label statusClip = new Label();
    private final Label statusAccess = new Label();
    private final Button refreshBtn = new Button();
    private final Button pillHome = pill("Home", Icons.HOME);
    private final Button pillMine = pill("My files", Icons.USER);
    private final Button pillCompany = pill("Company", Icons.SERVER);
    private final Button pillAdmin = pill("Admin", Icons.SHIELD);
    private final Button sortBtn = new Button("Name", Icons.of(Icons.SORT, 15));
    private final Button kindBtn = new Button("All types", Icons.of(Icons.FILTER, 15));
    private final Button gridBtn = new Button("", Icons.of(Icons.GRID, 15));
    private final Button listBtn = new Button("", Icons.of(Icons.LIST, 15));
    private final Button paneBtn = new Button("", Icons.of(Icons.PANEL, 15));

    private ToolButton backBtn, fwdBtn, upBtn, newBtn, uploadBtn, cutBtn, copyBtn, pasteBtn, renameBtn, deleteBtn;
    private ToolButton checkBtn, versionsBtn, shareBtn, convertBtn, signBtn, lockBtn, markBtn, downloadBtn, propsBtn;

    private Model.Roots roots;
    private Loc loc;
    private NodeInfo current;
    private List<Model.PathItem> currentPath = List.of();
    private List<NodeInfo> items = List.of();
    private List<NodeInfo> homeShared = List.of();
    private NodeInfo selected;
    private final Map<Long, Region> itemViews = new HashMap<>();
    private final Deque<Loc> back = new ArrayDeque<>();
    private final Deque<Loc> forward = new ArrayDeque<>();
    private NodeInfo clip;
    private boolean clipCut;
    private boolean listView;
    private boolean previewOn;
    private Sort sort = Sort.NAME;
    private String kind = "All";
    private boolean loading;
    private AdminView admin;

    public ExplorerView(WindowFrame frame, ApiClient api, AppConfig config, Model.UserInfo user, Runnable onSignOut) {
        this.frame = frame;
        this.api = api;
        this.config = config;
        this.user = user;
        this.onSignOut = onSignOut;
        this.listView = config.listView();
        this.previewOn = config.previewPane();
        build();
    }

    public Node root() { return root; }

    public void start() {
        Runnable go = () -> Async.run(api::roots, r -> {
            roots = r;
            buildSidebar();
            navigate(new Loc(Place.HOME, null), Mode.FRESH);
            checkForUpdate();
        }, this::fail);
        if (user.mustChangePassword()) DocDialogs.changePassword(frame, api, true, go);
        else go.run();
    }

    // ================================================================== layout

    private void build() {
        root.getStyleClass().add("explorer");
        frame.setTitleCenter(buildPills());
        frame.setTitleRight(buildAccount());

        toolbars.getChildren().addAll(buildAddressRow(), buildActionRow());
        toolbars.setPadding(new Insets(2, 14, 10, 14));
        root.setTop(toolbars);

        ScrollPane sideScroll = new ScrollPane(sidebar);
        sideScroll.setFitToWidth(true);
        sideScroll.getStyleClass().add("side-scroll");
        sidebar.getStyleClass().add("sidebar");
        StackPane sideCard = new StackPane(sideScroll);
        sideCard.getStyleClass().add("card");
        sideCard.setMinWidth(236);
        sideCard.setPrefWidth(236);
        sideCard.setMaxWidth(236);

        contentBox.getStyleClass().add("content-box");
        contentScroll.setFitToWidth(true);
        contentScroll.getStyleClass().add("content-scroll");
        dropHint.getStyleClass().add("drop-hint");
        dropHint.setVisible(false);
        dropHint.setMouseTransparent(true);
        dropHint.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        contentCard.getChildren().addAll(contentScroll, dropHint);
        contentCard.getStyleClass().add("card");
        HBox.setHgrow(contentCard, Priority.ALWAYS);
        installDragAndDrop();
        contentScroll.setOnMouseClicked(e -> {
            if (e.getTarget() == contentBox || e.getTarget() == contentScroll) select(null);
        });

        previewPane.getStyleClass().add("preview-pane");
        ScrollPane pvScroll = new ScrollPane(previewPane);
        pvScroll.setFitToWidth(true);
        pvScroll.getStyleClass().add("side-scroll");
        previewCard.getChildren().setAll(pvScroll);
        previewCard.getStyleClass().add("card");
        previewCard.setMinWidth(270);
        previewCard.setMaxWidth(270);

        body.getChildren().addAll(sideCard, contentCard);
        if (previewOn) body.getChildren().add(previewCard);
        body.setPadding(new Insets(0, 14, 0, 14));
        root.setCenter(body);

        statusCount.getStyleClass().add("strong");
        statusClip.getStyleClass().add("status-blue");
        Region g = new Region();
        HBox.setHgrow(g, Priority.ALWAYS);
        Label server = new Label("Connected to " + hostOf(api.baseUrl()));
        HBox status = new HBox(14, statusCount, statusSel, statusClip, g, statusAccess, server);
        status.getStyleClass().add("status-bar");
        status.setAlignment(Pos.CENTER_LEFT);
        root.setBottom(status);

        root.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
    }

    private Button pill(String text, String icon) {
        Button b = new Button(text, Icons.of(icon, 15));
        b.getStyleClass().add("pill");
        return b;
    }

    private Node buildPills() {
        pillHome.setOnAction(e -> navigate(new Loc(Place.HOME, null), Mode.PUSH));
        pillMine.setOnAction(e -> { if (roots != null) navigate(new Loc(Place.FOLDER, roots.personal().id()), Mode.PUSH); });
        pillCompany.setOnAction(e -> { if (roots != null) navigate(new Loc(Place.FOLDER, roots.company().id()), Mode.PUSH); });
        pillAdmin.setOnAction(e -> navigate(new Loc(Place.ADMIN, null), Mode.PUSH));
        HBox pills = new HBox(2, pillHome, pillMine, pillCompany);
        if (user.isAdmin()) pills.getChildren().add(pillAdmin);
        pills.getStyleClass().add("pill-group");
        pills.setAlignment(Pos.CENTER);
        pills.setFillHeight(false);
        pills.setMaxHeight(Region.USE_PREF_SIZE);
        return pills;
    }

    private Node buildAccount() {
        Label sync = new Label("● " + hostOf(api.baseUrl()));
        sync.getStyleClass().add("sync-chip");
        Label initials = new Label(user.initials());
        initials.getStyleClass().add("avatar");
        Button account = new Button(user.login(), initials);
        account.getStyleClass().add("account-btn");
        ContextMenu menu = new ContextMenu();
        MenuItem who = new MenuItem(user.displayName() + " · " + (user.department() == null ? "" : user.department()));
        who.setDisable(true);
        MenuItem role = new MenuItem(user.isAdmin() ? "Role: IT administrator" : "My files: \\\\SRV-FILES\\Users\\" + user.login());
        role.setDisable(true);
        MenuItem pwd = new MenuItem("Change password…");
        pwd.setOnAction(e -> DocDialogs.changePassword(frame, api, false, () -> { }));
        MenuItem out = new MenuItem("Sign out");
        out.getStyleClass().add("danger-item");
        out.setOnAction(e -> Async.run(api::logout, onSignOut, x -> onSignOut.run()));
        menu.getItems().addAll(who, role, new SeparatorMenuItem(), pwd, out);
        account.setOnAction(e -> menu.show(account, Side.BOTTOM, -140, 6));
        HBox box = new HBox(8, sync, account);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setFillHeight(false);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        return box;
    }

    private Node buildAddressRow() {
        Consumer<String> explain = msg -> Toast.show(frame, msg);
        backBtn = new ToolButton(Icons.BACK, null, "Back (Alt+Left)", this::goBack, explain);
        fwdBtn = new ToolButton(Icons.FORWARD, null, "Forward (Alt+Right)", this::goForward, explain);
        upBtn = new ToolButton(Icons.UP, null, "Up one level (Alt+Up)", this::goUp, explain);
        HBox nav = new HBox(2, backBtn, fwdBtn, upBtn);
        nav.getStyleClass().add("tool-group");

        crumbs.setAlignment(Pos.CENTER_LEFT);
        refreshBtn.setGraphic(Icons.of(Icons.REFRESH, 15));
        refreshBtn.getStyleClass().addAll("tb", "tb-icon");
        refreshBtn.setTooltip(new Tooltip("Refresh (F5)"));
        refreshBtn.setOnAction(e -> refresh());
        Region g = new Region();
        HBox.setHgrow(g, Priority.ALWAYS);
        HBox address = new HBox(4, FileIcon.folder(20, false), crumbs, g, refreshBtn);
        address.getStyleClass().addAll("tool-group", "address-bar");
        address.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(address, Priority.ALWAYS);

        search.setPromptText("Search in this folder");
        search.getStyleClass().add("search-field");
        search.textProperty().addListener((o, a, b) -> render(false));
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox searchBox = new HBox(8, Icons.of(Icons.SEARCH, 15), search);
        searchBox.getStyleClass().addAll("tool-group", "search-box");
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPrefWidth(250);
        searchBox.setMinWidth(160);

        HBox row = new HBox(8, nav, address, searchBox);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node buildActionRow() {
        Consumer<String> explain = msg -> Toast.show(frame, msg);
        newBtn = new ToolButton(Icons.PLUS, "New", "New folder (Ctrl+Shift+N)", this::newFolder, explain);
        uploadBtn = new ToolButton(Icons.UPLOAD, "Upload", "Upload files (Ctrl+U)", this::upload, explain);
        cutBtn = new ToolButton(Icons.CUT, null, "Cut (Ctrl+X)", () -> setClip(selected, true), explain);
        copyBtn = new ToolButton(Icons.COPY, null, "Copy (Ctrl+C)", () -> setClip(selected, false), explain);
        pasteBtn = new ToolButton(Icons.PASTE, null, "Paste (Ctrl+V)", this::paste, explain);
        renameBtn = new ToolButton(Icons.PENCIL, null, "Rename (F2)", () -> rename(selected), explain);
        deleteBtn = new ToolButton(Icons.TRASH, null, "Delete (Del)", () -> delete(selected), explain);
        downloadBtn = new ToolButton(Icons.DOWNLOAD, null, "Download to this computer (Ctrl+D)", () -> download(selected), explain);
        checkBtn = new ToolButton(Icons.EDIT_MARK, "Check out", "Check out to edit / check in (Ctrl+E)", () -> checkOutOrIn(selected), explain);
        versionsBtn = new ToolButton(Icons.HISTORY, null, "Version history", () -> props(selected, "Versions"), explain);
        shareBtn = new ToolButton(Icons.PEOPLE, "Share", "Share with a colleague", () -> DocDialogs.share(frame, api, selected, this::reload), explain);
        convertBtn = new ToolButton(Icons.CONVERT, "Convert", "Convert to another format", () -> DocDialogs.convert(frame, api, selected, this::afterAction), explain);
        signBtn = new ToolButton(Icons.SIGN, "Sign", "Sign with your certificate", () -> DocDialogs.sign(frame, api, user, selected, this::afterAction), explain);
        lockBtn = new ToolButton(Icons.LOCK, "Lock", "Lock with a password / unlock", () -> lockOrUnlock(selected), explain);
        markBtn = new ToolButton(Icons.STAMP, "Mark", "Stamp or watermark", () -> DocDialogs.stamp(frame, api, selected, this::afterAction), explain);
        propsBtn = new ToolButton(Icons.INFO, null, "Properties (Alt+Enter)", () -> props(selected != null ? selected : current, "General"), explain);

        HBox actions = new HBox(1, newBtn, uploadBtn, sep(), cutBtn, copyBtn, pasteBtn, renameBtn, deleteBtn, downloadBtn, sep(),
                checkBtn, versionsBtn, shareBtn, sep(), convertBtn, signBtn, lockBtn, markBtn, sep(), propsBtn);
        actions.getStyleClass().add("tool-group");
        actions.setAlignment(Pos.CENTER_LEFT);

        kindBtn.getStyleClass().add("tb");
        ContextMenu kindMenu = new ContextMenu();
        for (String k : FileKinds.GROUPS) {
            MenuItem mi = new MenuItem(k.equals("All") ? "All types" : k);
            mi.setOnAction(e -> {
                kind = k;
                kindBtn.setText(k.equals("All") ? "All types" : k);
                kindBtn.getStyleClass().remove("on-soft");
                if (!k.equals("All")) kindBtn.getStyleClass().add("on-soft");
                select(null);
                render(true);
            });
            kindMenu.getItems().add(mi);
        }
        kindBtn.setOnAction(e -> kindMenu.show(kindBtn, Side.BOTTOM, 0, 6));

        sortBtn.getStyleClass().add("tb");
        ContextMenu sortMenu = new ContextMenu();
        for (Sort s : Sort.values()) {
            String label = switch (s) {
                case NAME -> "Name";
                case DATE -> "Date modified";
                case TYPE -> "Type";
                case SIZE -> "Size";
            };
            MenuItem mi = new MenuItem(label);
            mi.setOnAction(e -> {
                sort = s;
                sortBtn.setText(label);
                render(true);
            });
            sortMenu.getItems().add(mi);
        }
        sortBtn.setOnAction(e -> sortMenu.show(sortBtn, Side.BOTTOM, 0, 6));
        for (Button b : List.of(gridBtn, listBtn, paneBtn)) b.getStyleClass().add("view-btn");
        (listView ? listBtn : gridBtn).getStyleClass().add("on");
        if (previewOn) paneBtn.getStyleClass().add("on-soft");
        gridBtn.setTooltip(new Tooltip("Large icons"));
        listBtn.setTooltip(new Tooltip("Details"));
        paneBtn.setTooltip(new Tooltip("Preview pane"));
        gridBtn.setOnAction(e -> setListView(false));
        listBtn.setOnAction(e -> setListView(true));
        paneBtn.setOnAction(e -> togglePreview());
        HBox view = new HBox(2, kindBtn, sortBtn, gridBtn, listBtn, paneBtn);
        view.getStyleClass().add("tool-group");
        view.setAlignment(Pos.CENTER_LEFT);

        for (Button b : List.of(kindBtn, sortBtn, gridBtn, listBtn, paneBtn)) b.setMinWidth(Region.USE_PREF_SIZE);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        view.setMinWidth(Region.USE_PREF_SIZE);
        Region g = new Region();
        HBox.setHgrow(g, Priority.ALWAYS);
        HBox wrap = new HBox(8, actions, g, view);
        wrap.setAlignment(Pos.CENTER_LEFT);
        wrap.setMinWidth(0);
        // Labels stay readable: when they don't fit, every labelled button switches to icon only (with its tooltip).
        double[] fullWidth = {0};
        ToolButton[] labelled = {newBtn, uploadBtn, checkBtn, shareBtn, convertBtn, signBtn, lockBtn, markBtn};
        Runnable fit = () -> {
            boolean compact = labelled[0].getContentDisplay() == ContentDisplay.GRAPHIC_ONLY;
            if (!compact) fullWidth[0] = actions.prefWidth(-1) + view.prefWidth(-1) + 24;
            boolean needCompact = wrap.getWidth() > 0 && wrap.getWidth() < fullWidth[0];
            if (needCompact != compact) for (ToolButton b : labelled) b.setCompact(needCompact);
        };
        wrap.widthProperty().addListener((o, a, w) -> fit.run());
        Platform.runLater(fit);
        return wrap;
    }

    private Separator sep() {
        Separator s = new Separator(Orientation.VERTICAL);
        s.getStyleClass().add("tool-sep");
        return s;
    }

    private void buildSidebar() {
        sidebar.getChildren().clear();
        addSection("QUICK ACCESS");
        sidebar.getChildren().add(sideItem("Home", Icons.HOME, 0, new Loc(Place.HOME, null), null, null));
        sidebar.getChildren().add(sideItem("Shared with me", Icons.PEOPLE, 0, new Loc(Place.SHARED, null),
                roots.sharedWithMe() > 0 ? String.valueOf(roots.sharedWithMe()) : null, null));
        sidebar.getChildren().add(sideSep());
        addSection("PERSONAL");
        sidebar.getChildren().add(sideItem("My files", Icons.USER, 0, new Loc(Place.FOLDER, roots.personal().id()), null, roots.personal()));
        int personalAt = sidebar.getChildren().size();
        sidebar.getChildren().add(sideSep());
        addSection("COMPANY");
        sidebar.getChildren().add(sideItem("Company", Icons.SERVER, 0, new Loc(Place.FOLDER, roots.company().id()), null, roots.company()));
        if (user.isAdmin()) {
            sidebar.getChildren().add(sideSep());
            addSection("IT");
            sidebar.getChildren().add(sideItem("Admin console", Icons.SHIELD, 0, new Loc(Place.ADMIN, null), null, null));
        }
        Async.run(() -> api.children(roots.personal().id()), kids -> {
            int at = personalAt;
            for (NodeInfo k : kids) if (!k.isFile()) sidebar.getChildren().add(at++, sideItem(k.name(), Icons.FOLDER, 1, new Loc(Place.FOLDER, k.id()), null, k));
            highlightSidebar();
        }, e -> { });
        Async.run(() -> api.children(roots.company().id()), kids -> {
            int at = indexOfKey("n:" + roots.company().id()) + 1;
            for (NodeInfo k : kids) if (!k.isFile()) sidebar.getChildren().add(at++, sideItem(k.name(), Icons.FOLDER, 1, new Loc(Place.FOLDER, k.id()), null, k));
            highlightSidebar();
        }, e -> { });
    }

    private int indexOfKey(String key) {
        for (int i = 0; i < sidebar.getChildren().size(); i++) {
            if (key.equals(sidebar.getChildren().get(i).getUserData())) return i;
        }
        return sidebar.getChildren().size() - 1;
    }

    private Region sideSep() {
        Region sep = new Region();
        sep.getStyleClass().add("side-sep");
        return sep;
    }

    private void addSection(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-label");
        l.setPadding(new Insets(6, 10, 6, 10));
        sidebar.getChildren().add(l);
    }

    private Button sideItem(String text, String icon, int depth, Loc target, String count, NodeInfo n) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox content = new HBox(9, Icons.of(icon, 16), label);
        content.setAlignment(Pos.CENTER_LEFT);
        if (count != null) content.getChildren().add(withClass(new Label(count), "side-count"));
        if (n != null && n.noAccess()) content.getChildren().add(withClass(Icons.of(Icons.LOCK, 13), "lock-mini"));
        else if (n != null && n.readOnly() && !n.isRoot() && !n.isPersonal()) content.getChildren().add(withClass(Icons.of(Icons.EYE, 13), "eye-mini"));
        Button b = new Button();
        b.setGraphic(content);
        b.getStyleClass().add("side-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setPadding(new Insets(0, 10, 0, 10 + depth * 18));
        b.setUserData(target.key());
        b.setOnAction(e -> {
            if (n != null && n.noAccess()) DocDialogs.requestAccess(frame, api, n.id(), n.name(), null);
            else navigate(target, Mode.PUSH);
        });
        return b;
    }

    private Node withClass(Node n, String cls) {
        n.getStyleClass().add(cls);
        return n;
    }

    private void highlightSidebar() {
        String key = loc == null ? "" : loc.key();
        for (Node n : sidebar.getChildren()) {
            if (n instanceof Button b && b.getUserData() instanceof String k) {
                b.getStyleClass().remove("active");
                if (k.equals(key)) b.getStyleClass().add("active");
            }
        }
    }

    // ================================================================== navigation

    private enum Mode { FRESH, PUSH, BACK, FORWARD }

    private record Loaded(NodeInfo folder, List<Model.PathItem> path, List<NodeInfo> children, List<NodeInfo> extra) { }

    private void navigate(Loc target, Mode mode) {
        if (loading) return;
        loading = true;
        Loc from = loc;
        Async.run(() -> switch (target.place()) {
            case HOME -> new Loaded(null, List.of(), api.recent(), api.sharedWithMe());
            case SHARED -> new Loaded(null, List.of(), api.sharedWithMe(), List.of());
            case ADMIN -> new Loaded(null, List.of(), List.of(), List.of());
            case FOLDER -> new Loaded(api.node(target.id()), api.path(target.id()), api.children(target.id()), List.of());
        }, r -> {
            loading = false;
            switch (mode) {
                case PUSH -> {
                    if (from != null && !from.equals(target)) back.push(from);
                    forward.clear();
                }
                case BACK -> { if (from != null) forward.push(from); }
                case FORWARD -> { if (from != null) back.push(from); }
                default -> { }
            }
            boolean same = target.equals(from);
            loc = target;
            current = r.folder();
            currentPath = r.path();
            items = r.children();
            homeShared = r.extra();
            if (!same) {
                selected = null;
                search.clear();
            } else if (selected != null) {
                Long sid = selected.id();
                selected = items.stream().filter(x -> x.id().equals(sid)).findFirst().orElse(null);
            }
            showPlace();
        }, e -> {
            loading = false;
            if (e instanceof ApiException a && a.isForbidden() && target.place() == Place.FOLDER) {
                DocDialogs.requestAccess(frame, api, target.id(), "this folder", a.getMessage());
            } else fail(e);
        });
    }

    private void showPlace() {
        boolean adminPlace = loc.place() == Place.ADMIN;
        if (adminPlace) {
            if (admin == null) admin = new AdminView(frame, api, user);
            root.setTop(null);
            root.setCenter(admin.root());
            admin.open("Users");
            Anim.fadeUp(admin.root(), 0);
        } else {
            if (root.getTop() == null) root.setTop(toolbars);
            if (root.getCenter() != body) root.setCenter(body);
        }
        renderCrumbs();
        if (!adminPlace) render(true);
        highlightSidebar();
        updatePills();
        updateToolbar();
    }

    private void reload() { reloadKeepSelection(selected == null ? null : selected.id()); }

    private void afterAction(NodeInfo result) { reloadKeepSelection(result == null ? null : result.id()); }

    private void reloadKeepSelection(Long selectId) {
        if (loc == null || loc.place() == Place.ADMIN) return;
        Loc target = loc;
        Async.run(() -> switch (target.place()) {
            case HOME -> new Loaded(null, List.of(), api.recent(), api.sharedWithMe());
            case SHARED -> new Loaded(null, List.of(), api.sharedWithMe(), List.of());
            default -> new Loaded(api.node(target.id()), api.path(target.id()), api.children(target.id()), List.of());
        }, r -> {
            current = r.folder();
            items = r.children();
            homeShared = r.extra();
            selected = selectId == null ? null : find(selectId);
            render(true);
            if (current != null && current.isRoot()) {
                Async.run(api::roots, rr -> {
                    roots = rr;
                    buildSidebar();
                }, e -> { });
            }
        }, this::fail);
    }

    private NodeInfo find(Long id) {
        for (NodeInfo n : items) if (n.id().equals(id)) return n;
        for (NodeInfo n : homeShared) if (n.id().equals(id)) return n;
        return null;
    }

    private void refresh() {
        if (loc == null) return;
        if (loc.place() == Place.ADMIN) {
            admin.open("Users");
            return;
        }
        RotateTransition spin = Anim.spin(refreshBtn.getGraphic());
        Loc target = loc;
        Async.run(() -> switch (target.place()) {
            case HOME -> new Loaded(null, List.of(), api.recent(), api.sharedWithMe());
            case SHARED -> new Loaded(null, List.of(), api.sharedWithMe(), List.of());
            default -> new Loaded(api.node(target.id()), api.path(target.id()), api.children(target.id()), List.of());
        }, r -> {
            spin.stop();
            refreshBtn.getGraphic().setRotate(0);
            current = r.folder();
            items = r.children();
            homeShared = r.extra();
            render(true);
            Toast.show(frame, "Up to date.");
        }, e -> {
            spin.stop();
            refreshBtn.getGraphic().setRotate(0);
            fail(e);
        });
    }

    private void goBack() { if (!back.isEmpty()) navigate(back.pop(), Mode.BACK); }

    private void goForward() { if (!forward.isEmpty()) navigate(forward.pop(), Mode.FORWARD); }

    private void goUp() {
        if (loc == null || loc.place() != Place.FOLDER || current == null) return;
        if (current.isSharedToMe() && currentPath.size() <= 1) {
            navigate(new Loc(Place.SHARED, null), Mode.PUSH);
            return;
        }
        if (current.parentId() != null) navigate(new Loc(Place.FOLDER, current.parentId()), Mode.PUSH);
    }

    private void updatePills() {
        for (Button b : List.of(pillHome, pillMine, pillCompany, pillAdmin)) b.getStyleClass().remove("on");
        if (loc == null) return;
        Button on = switch (loc.place()) {
            case HOME, SHARED -> pillHome;
            case ADMIN -> pillAdmin;
            case FOLDER -> current != null && current.isPersonal() && current.mine() ? pillMine : current != null && current.isPersonal() ? pillHome : pillCompany;
        };
        on.getStyleClass().add("on");
    }

    // ================================================================== rendering

    private void renderCrumbs() {
        crumbs.getChildren().clear();
        List<Object[]> parts = new ArrayList<>();
        switch (loc.place()) {
            case HOME -> parts.add(new Object[]{"Home", new Loc(Place.HOME, null)});
            case SHARED -> parts.add(new Object[]{"Shared with me", new Loc(Place.SHARED, null)});
            case ADMIN -> parts.add(new Object[]{"Admin console", new Loc(Place.ADMIN, null)});
            case FOLDER -> {
                if (current != null && current.isSharedToMe()) parts.add(new Object[]{"Shared with me", new Loc(Place.SHARED, null)});
                for (int i = 0; i < currentPath.size(); i++) {
                    Model.PathItem p = currentPath.get(i);
                    String label = p.name();
                    if (i == 0 && current != null && !current.isSharedToMe()) label = current.isPersonal() ? "My files" : "Company";
                    parts.add(new Object[]{label, new Loc(Place.FOLDER, p.id())});
                }
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) crumbs.getChildren().add(withClass(Icons.of(Icons.CHEVRON, 13), "crumb-sep"));
            Button b = new Button((String) parts.get(i)[0]);
            b.getStyleClass().add("crumb");
            if (i == parts.size() - 1) b.getStyleClass().add("last");
            Loc l = (Loc) parts.get(i)[1];
            b.setOnAction(e -> navigate(l, Mode.PUSH));
            crumbs.getChildren().add(b);
        }
    }

    private void setListView(boolean on) {
        listView = on;
        config.setListView(on);
        gridBtn.getStyleClass().remove("on");
        listBtn.getStyleClass().remove("on");
        (on ? listBtn : gridBtn).getStyleClass().add("on");
        render(true);
    }

    private void togglePreview() {
        previewOn = !previewOn;
        config.setPreviewPane(previewOn);
        paneBtn.getStyleClass().remove("on-soft");
        if (previewOn) {
            paneBtn.getStyleClass().add("on-soft");
            body.getChildren().add(previewCard);
            Anim.fadeIn(previewCard, 200);
            renderPreview();
        } else body.getChildren().remove(previewCard);
    }

    private void render(boolean animate) {
        contentBox.getChildren().clear();
        itemViews.clear();
        if (loc == null || loc.place() == Place.ADMIN) return;

        Node bar = infoBar();
        if (bar != null) contentBox.getChildren().add(bar);

        if (loc.place() == Place.HOME) {
            contentBox.getChildren().add(sectionTitle("Recent files"));
            contentBox.getChildren().add(filtered(items).isEmpty() ? emptyNote("Files you open or change will show up here.") : itemsView(filtered(items), animate));
            contentBox.getChildren().add(sectionTitle("Shared with me"));
            contentBox.getChildren().add(homeShared.isEmpty() ? emptyNote("Nobody has shared anything with you yet.") : itemsView(filtered(homeShared), animate));
        } else {
            List<NodeInfo> shown = filtered(items);
            if (shown.isEmpty()) contentBox.getChildren().add(emptyState(!search.getText().isBlank() || !kind.equals("All")));
            else contentBox.getChildren().add(itemsView(shown, animate));
        }
        refreshSelectionStyles();
        updateToolbar();
        int total = loc.place() == Place.HOME ? filtered(items).size() + filtered(homeShared).size() : filtered(items).size();
        statusCount.setText(total + (total == 1 ? " item" : " items"));
        statusAccess.setText(current == null ? "" : current.isSharedToMe() ? "Shared with you · " + nullTo(current.shareLevel(), "")
                : "Your access: " + current.accessLabel());
    }

    private List<NodeInfo> filtered(List<NodeInfo> list) {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<NodeInfo> out = new ArrayList<>();
        for (NodeInfo n : list) {
            if (!q.isEmpty() && !n.fullName().toLowerCase(Locale.ROOT).contains(q)) continue;
            if (!kind.equals("All") && (!n.isFile() || !FileKinds.group(n.ext()).equals(kind))) continue;
            out.add(n);
        }
        out.sort(comparator());
        return out;
    }

    private Node itemsView(List<NodeInfo> shown, boolean animate) {
        if (listView) {
            VBox list = new VBox(2);
            list.getChildren().add(listHeader());
            int i = 0;
            for (NodeInfo n : shown) {
                Region row = listRow(n);
                itemViews.put(n.id(), row);
                list.getChildren().add(row);
                if (animate) Anim.fadeUp(row, Math.min(i++ * 18, 240));
            }
            return list;
        }
        FlowPane grid = new FlowPane(6, 6);
        grid.getStyleClass().add("tile-grid");
        int i = 0;
        for (NodeInfo n : shown) {
            Region tile = tile(n);
            itemViews.put(n.id(), tile);
            grid.getChildren().add(tile);
            if (animate) Anim.fadeUp(tile, Math.min(i++ * 22, 300));
        }
        return grid;
    }

    private Label sectionTitle(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("section-title");
        return l;
    }

    private Node emptyNote(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("muted");
        l.setPadding(new Insets(4, 4, 10, 4));
        return l;
    }

    private Comparator<NodeInfo> comparator() {
        Comparator<NodeInfo> foldersFirst = Comparator.comparing(NodeInfo::isFile);
        Comparator<NodeInfo> byName = Comparator.comparing(n -> n.fullName().toLowerCase(Locale.ROOT));
        Comparator<java.time.Instant> newest = Comparator.nullsLast(Comparator.<java.time.Instant>reverseOrder());
        return switch (sort) {
            case NAME -> foldersFirst.thenComparing(byName);
            case DATE -> foldersFirst.thenComparing(NodeInfo::modifiedAt, newest).thenComparing(byName);
            case TYPE -> foldersFirst.thenComparing(n -> n.ext() == null ? "" : n.ext()).thenComparing(byName);
            case SIZE -> foldersFirst.thenComparing(n -> -n.sizeBytes()).thenComparing(byName);
        };
    }

    private Node infoBar() {
        String text = null, cls = "info-bar";
        Node icon = null;
        if (loc.place() == Place.SHARED) {
            text = "Files and folders colleagues shared with you from their own My files. What you can do depends on what they allowed.";
            icon = Icons.of(Icons.PEOPLE, 16);
            cls = "info-bar-purple";
        } else if (loc.place() == Place.FOLDER && current != null) {
            if (current.isRoot() && current.isPersonal()) {
                text = "Your private folder on \\\\SRV-FILES\\Users\\" + user.login() + ". Only you can see these files, unless you share them.";
                icon = Icons.of(Icons.USER, 16);
            } else if (current.isRoot()) {
                text = "Company folders on \\\\SRV-FILES. What you can do in each folder depends on the access IT gave you.";
                icon = Icons.of(Icons.SERVER, 16);
            } else if (current.isSharedToMe()) {
                text = "Shared with you by " + nullTo(current.sharedByName(), "a colleague") + " · " + nullTo(current.shareLevel(), "") + ".";
                icon = Icons.of(Icons.PEOPLE, 16);
                cls = "info-bar-purple";
            } else if (!current.isPersonal() && current.readOnly()) {
                text = "Read only. You can open, copy, download and convert files here, but you can’t change this folder.";
                icon = Icons.of(Icons.EYE, 16);
                cls = "info-bar-muted";
            }
        }
        if (text == null) return null;
        Label l = new Label(text);
        l.setWrapText(true);
        HBox bar = new HBox(10, icon, l);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add(cls);
        Anim.fadeIn(bar, 250);
        return bar;
    }

    private Node emptyState(boolean filtering) {
        Node folder = FileIcon.folder(90, false);
        folder.setOpacity(0.35);
        String text = filtering ? "Nothing here matches your search or filter."
                : loc.place() == Place.SHARED ? "Nobody has shared anything with you yet." : "This folder is empty.";
        Label t = new Label(text);
        t.getStyleClass().add("empty-text");
        VBox box = new VBox(12, folder, t);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(70, 20, 70, 20));
        if (!filtering && canWriteHere()) {
            Button up = new Button("Upload files");
            up.getStyleClass().addAll("btn", "btn-primary");
            up.setOnAction(e -> upload());
            box.getChildren().addAll(up, withClass(new Label("or drag files here from your computer"), "muted-small"));
        }
        Anim.pop(box);
        return box;
    }

    private Region tile(NodeInfo n) {
        Label name = new Label(n.fullName());
        name.getStyleClass().add("tile-name");
        name.setWrapText(true);
        name.setMaxHeight(34);
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setAlignment(Pos.TOP_CENTER);
        name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        Label sub = new Label(subText(n));
        sub.getStyleClass().add(n.checkedOutByOther() ? "tile-sub-warn" : n.checkedOutByMe() ? "tile-sub-blue" : "tile-sub");
        VBox t = new VBox(8, FileIcon.big(n), name, sub);
        t.setAlignment(Pos.TOP_CENTER);
        t.getStyleClass().add("tile");
        if (n.noAccess()) t.setOpacity(0.6);
        hookItem(t, n);
        return t;
    }

    private Region listHeader() {
        HBox h = new HBox(12, cell("Name", -1), cell("Date modified", 150), cell("Type", 140), cell("Size", 80), cell("Access", 130));
        h.getStyleClass().add("list-header");
        return h;
    }

    private Region listRow(NodeInfo n) {
        Label name = new Label(n.fullName());
        name.getStyleClass().add("row-name");
        HBox nameBox = new HBox(10, FileIcon.small(n), name);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nameBox, Priority.ALWAYS);
        nameBox.setMinWidth(0);
        if (n.checkedOutByName() != null) nameBox.getChildren().add(withClass(new Label(n.checkedOutByMe() ? "You’re editing" : "Editing: " + n.checkedOutByName()),
                n.checkedOutByMe() ? "tag-blue" : "tag-orange"));
        String acc = n.isSharedToMe() ? nullTo(n.shareLevel(), "Shared") : n.isPersonal() ? (n.sharedCount() > 0 ? "Shared" : "Only you") : n.accessLabel();
        Label access = new Label(acc);
        access.getStyleClass().addAll("access-chip", n.isSharedToMe() || n.sharedCount() > 0 ? "access-shared"
                : "access-" + (n.isPersonal() ? "mine" : n.access().toLowerCase(Locale.ROOT)));
        HBox row = new HBox(12, nameBox, cell(Format.when(n.modifiedAt()), 150),
                cell(n.isFile() ? FileKinds.of(n.ext()).label() : "File folder", 140),
                cell(n.isFile() ? Format.size(n.sizeBytes()) : "", 80), fixed(access, 130));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        if (n.noAccess()) row.setOpacity(0.6);
        hookItem(row, n);
        return row;
    }

    private Label cell(String text, double width) {
        Label l = new Label(text);
        l.getStyleClass().add("cell");
        if (width > 0) {
            l.setMinWidth(width);
            l.setPrefWidth(width);
            l.setMaxWidth(width);
        } else {
            l.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(l, Priority.ALWAYS);
        }
        return l;
    }

    private Node fixed(Node n, double width) {
        HBox b = new HBox(n);
        b.setMinWidth(width);
        b.setPrefWidth(width);
        b.setMaxWidth(width);
        return b;
    }

    private String subText(NodeInfo n) {
        if (n.checkedOutByMe()) return "You’re editing";
        if (n.checkedOutByOther()) return "Editing: " + n.checkedOutByName();
        if (n.isSharedToMe() && n.sharedByName() != null && loc.place() != Place.FOLDER) return "From " + n.sharedByName();
        if (n.isFile()) return Format.size(n.sizeBytes()) + (n.sharedCount() > 0 ? " · Shared" : "");
        if (!n.isPersonal()) return n.accessLabel();
        return n.childCount() + (n.childCount() == 1 ? " item" : " items") + (n.sharedCount() > 0 ? " · Shared" : "");
    }

    private void hookItem(Region view, NodeInfo n) {
        view.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                select(n);
                if (e.getClickCount() == 2) open(n);
            }
            e.consume();
        });
        view.setOnContextMenuRequested(e -> {
            select(n);
            itemMenu(n).show(view, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    private void select(NodeInfo n) {
        selected = n;
        refreshSelectionStyles();
        updateToolbar();
        renderPreview();
    }

    private void refreshSelectionStyles() {
        for (Map.Entry<Long, Region> e : itemViews.entrySet()) {
            boolean on = selected != null && e.getKey().equals(selected.id());
            e.getValue().getStyleClass().remove("selected");
            if (on) e.getValue().getStyleClass().add("selected");
        }
        statusSel.setText(selected == null ? "" : "1 selected" + (selected.isFile() ? " · " + Format.size(selected.sizeBytes()) : ""));
    }

    // ================================================================== preview pane

    private void renderPreview() {
        if (!previewOn) return;
        previewPane.getChildren().clear();
        NodeInfo n = selected;
        if (n == null) {
            Label hint = new Label("Select a file or folder to see it here.");
            hint.getStyleClass().add("muted");
            hint.setWrapText(true);
            VBox v = new VBox(10, Icons.of(Icons.INFO, 24), hint);
            v.setAlignment(Pos.CENTER);
            v.setPadding(new Insets(60, 10, 10, 10));
            previewPane.getChildren().add(v);
            return;
        }
        StackPane art = new StackPane(FileIcon.big(n));
        art.getStyleClass().add("preview-art");
        Label name = new Label(n.fullName());
        name.getStyleClass().add("preview-name");
        name.setWrapText(true);
        Label meta = new Label((n.isFile() ? FileKinds.of(n.ext()).label() + " · " + Format.size(n.sizeBytes()) : "File folder")
                + (n.isFile() && n.version() > 1 ? " · v" + n.version() : ""));
        meta.getStyleClass().add("muted-small");
        VBox head = new VBox(4, name, meta);
        previewPane.getChildren().addAll(art, head);
        if (n.checkedOutByName() != null) previewPane.getChildren().add(pill(Icons.PENCIL, n.checkedOutByMe() ? "You checked this out" : n.checkedOutByName() + " is editing", n.checkedOutByMe() ? "pv-blue" : "pv-orange"));
        if (n.locked()) previewPane.getChildren().add(pill(Icons.LOCK, "Password-locked", "pv-grey"));
        if (n.signed()) previewPane.getChildren().add(pill(Icons.SIGN, "Signed", "pv-green"));
        if (n.stamp() != null) previewPane.getChildren().add(pill(Icons.STAMP, "Stamped “" + n.stamp() + "”", "pv-amber"));
        if (n.sharedCount() > 0) previewPane.getChildren().add(pill(Icons.PEOPLE, "Shared with " + n.sharedCount() + (n.sharedCount() == 1 ? " person" : " people"), "pv-purple"));
        if (n.isSharedToMe()) previewPane.getChildren().add(pill(Icons.PEOPLE, "From " + n.sharedByName() + " · " + n.shareLevel(), "pv-purple"));
        Button open = new Button(n.isFile() ? "Open" : "Open folder");
        open.getStyleClass().addAll("btn", "btn-primary");
        open.setMaxWidth(Double.MAX_VALUE);
        open.setOnAction(e -> open(n));
        previewPane.getChildren().add(open);
        if (n.isFile()) {
            GridPane grid = new GridPane();
            grid.setHgap(6);
            grid.setVgap(6);
            ToolButton[] bs = {convertBtn, signBtn, lockBtn, markBtn, checkBtn, shareBtn};
            String[][] defs = {{Icons.CONVERT, "Convert"}, {Icons.SIGN, "Sign"}, {Icons.LOCK, n.locked() ? "Unlock" : "Lock"}, {Icons.STAMP, "Mark"},
                    {Icons.EDIT_MARK, n.checkedOutByMe() ? "Check in" : "Check out"}, {Icons.PEOPLE, "Share"}};
            for (int i = 0; i < bs.length; i++) {
                ToolButton src = bs[i];
                Button b = new Button(defs[i][1], Icons.of(defs[i][0], 14));
                b.getStyleClass().addAll("pv-action");
                if (!src.allowed()) b.getStyleClass().add("off");
                b.setMaxWidth(Double.MAX_VALUE);
                b.setOnAction(e -> src.fire(true));
                GridPane.setHgrow(b, Priority.ALWAYS);
                grid.add(b, i % 2, i / 2);
            }
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(50);
            grid.getColumnConstraints().addAll(c, c);
            previewPane.getChildren().add(grid);
        }
        Button props = new Button("Properties", Icons.of(Icons.INFO, 14));
        props.getStyleClass().add("pv-action");
        props.setMaxWidth(Double.MAX_VALUE);
        props.setOnAction(e -> props(n, "General"));
        previewPane.getChildren().add(props);
        Anim.fadeIn(previewPane, 160);
    }

    private Node pill(String icon, String text, String cls) {
        Label l = new Label(text);
        l.setWrapText(true);
        HBox b = new HBox(8, Icons.of(icon, 14), l);
        b.setAlignment(Pos.CENTER_LEFT);
        b.getStyleClass().addAll("pv-pill", cls);
        return b;
    }

    // ================================================================== menus

    private ContextMenu itemMenu(NodeInfo n) {
        ContextMenu m = new ContextMenu();
        if (n.isFile()) {
            m.getItems().add(item("Open in Docket", new KeyCodeCombination(KeyCode.ENTER), () -> open(n), true));
            m.getItems().add(item("Open in desktop app", null, () -> openInApp(n), true));
            m.getItems().add(item("Download to this computer", new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN), () -> download(n), true));
            m.getItems().add(new SeparatorMenuItem());
            if (n.checkedOutByMe()) {
                m.getItems().add(item("Check in…", new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN), () -> checkIn(n), true));
                m.getItems().add(item("Discard check-out", null, () -> discard(n), true));
            } else {
                m.getItems().add(item("Check out for editing", new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN), () -> checkOut(n), checkBtn.allowed()));
            }
            m.getItems().add(item("Version history", null, () -> props(n, "Versions"), true));
            m.getItems().add(new SeparatorMenuItem());
            m.getItems().add(item("Convert…", null, () -> DocDialogs.convert(frame, api, n, this::afterAction), convertBtn.allowed()));
            m.getItems().add(item("Sign…", null, () -> DocDialogs.sign(frame, api, user, n, this::afterAction), signBtn.allowed()));
            m.getItems().add(item(n.locked() ? "Unlock…" : "Lock with password…", null, () -> lockOrUnlock(n), lockBtn.allowed()));
            m.getItems().add(item("Stamp…", null, () -> DocDialogs.stamp(frame, api, n, this::afterAction), markBtn.allowed()));
        } else {
            m.getItems().add(item("Open folder", new KeyCodeCombination(KeyCode.ENTER), () -> open(n), !n.noAccess()));
        }
        m.getItems().add(new SeparatorMenuItem());
        m.getItems().add(item("Share…", null, () -> DocDialogs.share(frame, api, n, this::reload), shareBtn.allowed()));
        m.getItems().add(item("Cut", new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN), () -> setClip(n, true), cutBtn.allowed()));
        m.getItems().add(item("Copy", new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN), () -> setClip(n, false), copyBtn.allowed()));
        if (!n.isFile() && clip != null) m.getItems().add(item("Paste into folder", null, () -> pasteInto(n), n.canWrite()));
        m.getItems().add(item("Rename", new KeyCodeCombination(KeyCode.F2), () -> rename(n), renameBtn.allowed()));
        MenuItem del = item("Delete", new KeyCodeCombination(KeyCode.DELETE), () -> delete(n), deleteBtn.allowed());
        del.getStyleClass().add("danger-item");
        m.getItems().add(del);
        m.getItems().add(new SeparatorMenuItem());
        m.getItems().add(item("Properties", new KeyCodeCombination(KeyCode.ENTER, KeyCombination.ALT_DOWN), () -> props(n, "General"), true));
        m.setOnShown(e -> Anim.menuIn(m.getSkin().getNode()));
        return m;
    }

    private MenuItem item(String text, KeyCombination key, Runnable r, boolean enabled) {
        MenuItem mi = new MenuItem(text);
        if (key != null) mi.setAccelerator(key);
        mi.setOnAction(e -> r.run());
        mi.setDisable(!enabled);
        return mi;
    }

    // ================================================================== permissions on the client (the server checks again)

    private boolean canWriteHere() {
        if (loc == null || loc.place() != Place.FOLDER || current == null) return false;
        if (current.isRoot() && !current.isPersonal()) return user.isAdmin();
        return current.canWrite();
    }

    private String whyNotWriteHere() {
        if (loc == null || loc.place() != Place.FOLDER) return "Open a folder first.";
        if (canWriteHere()) return null;
        if (current.isRoot() && !current.isPersonal()) return "Only IT can add folders at the top of Company.";
        if (current.isSharedToMe()) return nullTo(current.sharedByName(), "The owner") + " shared this with you as view only.";
        return "You have " + current.accessLabel().toLowerCase(Locale.ROOT) + " access to " + current.name() + ".";
    }

    private String whyNotStructure(NodeInfo n) {
        if (n == null) return "Select a file or folder first.";
        if (n.isRoot()) return "This location can’t be changed.";
        if (loc.place() != Place.FOLDER) return "Open the folder it is in to do that.";
        String w = whyNotWriteHere();
        if (w != null) return w;
        if (n.checkedOutByOther()) return n.checkedOutByName() + " is editing “" + n.fullName() + "” right now.";
        return null;
    }

    private String whyNotModify(NodeInfo n, String what) {
        if (n == null) return "Select a file first.";
        if (!n.isFile()) return "Choose a file, not a folder.";
        if (n.checkedOutByOther()) return n.checkedOutByName() + " is editing “" + n.fullName() + "” right now.";
        if (!n.canWrite()) return "You have read-only access to " + n.fullName() + ".";
        if (n.locked() && !what.equals("checkout") && !what.equals("lock")) return "Locked files can’t be changed. Unlock it first.";
        return switch (what) {
            case "sign" -> FileKinds.canSign(n.ext()) ? null : FileKinds.plural(n.ext()) + " can’t be signed.";
            case "mark" -> FileKinds.canStamp(n.ext()) ? null : FileKinds.plural(n.ext()) + " can’t be stamped.";
            default -> null;
        };
    }

    private void updateToolbar() {
        if (backBtn == null) return;
        backBtn.setWhyNot(back.isEmpty() ? "No previous place." : null);
        fwdBtn.setWhyNot(forward.isEmpty() ? "No next place." : null);
        upBtn.setWhyNot(loc == null || loc.place() != Place.FOLDER || current == null || current.isRoot() ? "You are at the top." : null);
        newBtn.setWhyNot(whyNotWriteHere());
        uploadBtn.setWhyNot(whyNotWriteHere());
        NodeInfo s = selected;
        cutBtn.setWhyNot(whyNotStructure(s));
        copyBtn.setWhyNot(s == null ? "Select a file or folder first." : s.isRoot() ? "This location can’t be copied." : null);
        pasteBtn.setWhyNot(clip == null ? "Nothing to paste. Cut or copy something first." : whyNotWriteHere());
        renameBtn.setWhyNot(whyNotStructure(s));
        deleteBtn.setWhyNot(whyNotStructure(s));
        downloadBtn.setWhyNot(s == null ? "Select a file first." : !s.isFile() ? "Folders can’t be downloaded. Open the folder instead." : null);
        boolean mineOut = s != null && s.checkedOutByMe();
        checkBtn.setText(mineOut ? "Check in" : "Check out");
        checkBtn.setWhyNot(mineOut ? null : whyNotModify(s, "checkout"));
        versionsBtn.setWhyNot(s == null || !s.isFile() ? "Select a file first." : null);
        shareBtn.setWhyNot(s == null ? "Select a file or folder in My files first." : s.isRoot() ? "Share a folder or file instead of all of My files."
                : !s.mine() ? "Only items in your own My files can be shared. Company folders are shared by IT." : null);
        convertBtn.setWhyNot(s == null ? "Select a file first." : !s.isFile() ? "Choose a file, not a folder." : null);
        signBtn.setWhyNot(whyNotModify(s, "sign"));
        lockBtn.setWhyNot(whyNotModify(s, "lock"));
        lockBtn.setText(s != null && s.locked() ? "Unlock" : "Lock");
        markBtn.setWhyNot(whyNotModify(s, "mark"));
        propsBtn.setWhyNot(s == null && current == null ? "Select a file or folder first." : null);
        statusClip.setText(clip == null ? "" : (clipCut ? "Cut: " : "Copied: ") + clip.fullName());
    }

    // ================================================================== actions

    private void open(NodeInfo n) {
        if (!n.isFile()) {
            if (n.noAccess()) {
                DocDialogs.requestAccess(frame, api, n.id(), n.name(), null);
                return;
            }
            navigate(new Loc(Place.FOLDER, n.id()), Mode.PUSH);
            return;
        }
        ViewerView.show(frame, api, n, new ViewerView.Actions() {
            @Override
            public void openInApp(NodeInfo x) { ExplorerView.this.openInApp(x); }

            @Override
            public void download(NodeInfo x) { ExplorerView.this.download(x); }

            @Override
            public void checkout(NodeInfo x) { checkOut(x); }

            @Override
            public void document(String action, NodeInfo x) {
                switch (action) {
                    case "convert" -> DocDialogs.convert(frame, api, x, ExplorerView.this::afterAction);
                    case "sign" -> DocDialogs.sign(frame, api, user, x, ExplorerView.this::afterAction);
                    case "lock" -> lockOrUnlock(x);
                    default -> DocDialogs.stamp(frame, api, x, ExplorerView.this::afterAction);
                }
            }

            @Override
            public void extracted(NodeInfo folder) { reloadKeepSelection(folder.id()); }
        });
    }

    /** Opens the file in the program Windows uses for it (the working copy if you checked it out). */
    private void openInApp(NodeInfo n) {
        Path working = n.checkedOutByMe() ? CheckoutStore.workingCopy(n.id()) : null;
        Path target = working != null ? working : AppConfig.tempDir().resolve(String.valueOf(n.id())).resolve(n.fullName());
        Toast.show(frame, "Opening " + n.fullName() + "…" + (working == null && n.canWrite() && !n.checkedOutByMe() ? " Check out first if you want to save changes back." : ""));
        Async.run(() -> {
            if (working == null) api.download(n.id(), target, false);
            openWithSystem(target, n.ext());
            return Boolean.TRUE;
        }, ok -> { }, this::fail);
    }

    private static void openWithSystem(Path file, String ext) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new ApiException(0, "This computer can’t open files from Docket directly. Use Download instead.");
        }
        try {
            Desktop.getDesktop().open(file.toFile());
        } catch (Exception ex) {
            throw new ApiException(0, "Windows has no app to open ." + ext + " files. Use Download instead.");
        }
    }

    private void download(NodeInfo n) {
        if (n == null || !n.isFile()) return;
        Path dir = Path.of(System.getProperty("user.home"), "Downloads");
        Path target = uniquePath(dir, n.fullName());
        Toast.Progress p = Toast.progress(frame, "Downloading " + n.fullName() + "…");
        p.bar().setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        Async.run(() -> {
            api.download(n.id(), target, true);
            return target;
        }, saved -> {
            p.close();
            Toast.show(frame, "Saved " + saved.getFileName() + " to Downloads.", "Show in folder", () -> showInFolder(saved));
        }, e -> {
            p.close();
            fail(e);
        });
    }

    private void newFolder() {
        String where = "In " + (current.isRoot() ? (current.isPersonal() ? "My files" : "Company") : current.name());
        Dialogs.prompt(frame, "New folder", where, "Name", "New folder", null, "Create",
                name -> Async.run(() -> api.createFolder(current.id(), name), created -> {
                    Toast.show(frame, "Created " + created.fullName() + ".");
                    reloadKeepSelection(created.id());
                }, this::fail));
    }

    private void upload() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Upload to " + current.name());
        List<File> files = fc.showOpenMultipleDialog(frame.stage());
        if (files != null && !files.isEmpty()) uploadFiles(files);
    }

    private void uploadFiles(List<File> files) {
        List<File> regular = files.stream().filter(File::isFile).toList();
        if (regular.isEmpty()) {
            Toast.show(frame, "Folders can’t be uploaded yet. Choose files instead.");
            return;
        }
        long folderId = current.id();
        String folderName = current.isRoot() ? (current.isPersonal() ? "My files" : "Company") : current.name();
        Toast.Progress p = Toast.progress(frame, "Uploading " + regular.size() + (regular.size() == 1 ? " file" : " files") + " to " + folderName);
        Async.run(() -> {
            List<String> failed = new ArrayList<>();
            Long last = null;
            for (int i = 0; i < regular.size(); i++) {
                File f = regular.get(i);
                int idx = i;
                Platform.runLater(() -> p.update("Uploading " + f.getName() + " (" + (idx + 1) + " of " + regular.size() + ")", (double) idx / regular.size()));
                try {
                    last = api.upload(folderId, f.toPath()).id();
                } catch (ApiException ex) {
                    failed.add(f.getName() + ": " + ex.getMessage());
                }
            }
            return new UploadResult(regular.size() - failed.size(), failed, last);
        }, r -> {
            p.close();
            if (current != null && current.id() == folderId) reloadKeepSelection(r.lastId());
            if (r.failed().isEmpty()) Toast.show(frame, "Uploaded " + r.ok() + (r.ok() == 1 ? " file" : " files") + " to " + folderName + ".");
            else Dialogs.message(frame, "Some files weren’t uploaded", String.join("\n", r.failed()), true);
        }, e -> {
            p.close();
            fail(e);
        });
    }

    private record UploadResult(int ok, List<String> failed, Long lastId) { }

    private void setClip(NodeInfo n, boolean cut) {
        if (n == null) return;
        clip = n;
        clipCut = cut;
        updateToolbar();
        Toast.show(frame, (cut ? "Cut " : "Copied ") + n.fullName() + ". Open a folder and press Paste.");
    }

    private void paste() {
        if (clip == null || current == null) return;
        pasteInto(current);
    }

    private void pasteInto(NodeInfo folder) {
        NodeInfo src = clip;
        boolean cut = clipCut;
        Async.run(() -> cut ? api.move(src.id(), folder.id()) : api.copy(src.id(), folder.id()), r -> {
            if (cut) clip = null;
            Toast.show(frame, (cut ? "Moved " : "Pasted ") + src.fullName() + " into " + (folder.isRoot() ? (folder.isPersonal() ? "My files" : "Company") : folder.name()) + ".");
            reloadKeepSelection(folder.id().equals(current == null ? null : current.id()) ? r.id() : null);
        }, this::fail);
    }

    private void rename(NodeInfo n) {
        if (n == null) return;
        String why = whyNotStructure(n);
        if (why != null) {
            Toast.show(frame, why);
            return;
        }
        Dialogs.prompt(frame, "Rename", n.fullName(), "New name", n.name(), n.isFile() && n.ext() != null ? "." + n.ext() : null, "Rename",
                name -> Async.run(() -> api.rename(n.id(), name), r -> {
                    Toast.show(frame, "Renamed to " + r.fullName() + ".");
                    reloadKeepSelection(r.id());
                }, this::fail));
    }

    private void delete(NodeInfo n) {
        if (n == null) return;
        String why = whyNotStructure(n);
        if (why != null) {
            Toast.show(frame, why);
            return;
        }
        Dialogs.confirm(frame, "Delete " + (n.isFile() ? "file" : "folder") + "?", n.fullName(),
                "It will be moved to the Recycle Bin on the server. You can undo this right after; IT can restore it within 30 days.", "Delete", true,
                () -> Async.run(() -> api.delete(n.id()), () -> {
                    reloadKeepSelection(null);
                    Toast.show(frame, n.fullName() + " moved to the Recycle Bin.", "Undo", () -> Async.run(() -> api.restore(n.id()), r -> {
                        Toast.show(frame, "Restored " + r.fullName() + ".");
                        reloadKeepSelection(r.id());
                    }, this::fail));
                }, this::fail));
    }

    private void props(NodeInfo n, String tab) {
        if (n == null) return;
        DocDialogs.properties(frame, api, n, tab, this::reload, f -> {
            try {
                openWithSystem(f, n.ext());
            } catch (ApiException e) {
                Toast.error(frame, e.getMessage());
            }
        });
    }

    private void lockOrUnlock(NodeInfo n) {
        if (n == null) return;
        if (n.locked()) DocDialogs.unlock(frame, api, n, this::afterAction);
        else DocDialogs.lock(frame, api, n, this::afterAction);
    }

    // ------------------------------------------------------------------ check-out / check-in

    private void checkOutOrIn(NodeInfo n) {
        if (n == null) return;
        if (n.checkedOutByMe()) checkIn(n);
        else checkOut(n);
    }

    private void checkOut(NodeInfo n) {
        String why = whyNotModify(n, "checkout");
        if (why != null) {
            Toast.show(frame, why);
            return;
        }
        Path working = CheckoutStore.pathFor(n.id(), n.fullName());
        Async.run(() -> {
            NodeInfo r = api.checkout(n.id());
            CheckoutStore.forget(n.id());
            api.download(n.id(), working, false);
            CheckoutStore.remember(n.id(), working);
            try {
                openWithSystem(working, n.ext());
            } catch (ApiException ignored) {
                // Still checked out; the user can open the working copy later with Open in desktop app.
            }
            return r;
        }, r -> {
            Toast.show(frame, "Checked out " + n.fullName() + ". Edit and save it, then press Check in. Others can open it but can’t change it.");
            reloadKeepSelection(r.id());
        }, this::fail);
    }

    private void checkIn(NodeInfo n) {
        Path working = CheckoutStore.workingCopy(n.id());
        boolean changed = working != null && CheckoutStore.changed(n.id());
        Label status = new Label(working == null ? "No working copy on this PC. Checking in only releases the file."
                : changed ? "Your changes will be saved as version " + (n.version() + 1) + "."
                : "You haven’t changed the file since you checked it out. Checking in releases it without a new version.");
        status.getStyleClass().add(changed ? "callout" : "callout-muted");
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        TextField note = new TextField();
        note.setPromptText("What did you change? e.g. Updated payment terms");
        note.getStyleClass().add("field");
        Hyperlink discard = new Hyperlink("Discard check-out and my changes");
        discard.getStyleClass().addAll("link", "danger-text");
        Dialogs.Handle[] h0 = new Dialogs.Handle[1];
        discard.setOnAction(e -> {
            h0[0].close();
            discard(n);
        });
        VBox body = new VBox(10, status, DocDialogs.label("What did you change?", "field-label"), note, discard);
        h0[0] = Dialogs.show(frame, "Check in", n.fullName(), body, "Check in", false, true, 500, h -> {
            h.busy("Checking in…");
            Async.run(() -> api.checkin(n.id(), note.getText(), changed ? working : null), r -> {
                CheckoutStore.forget(n.id());
                h.close();
                Toast.show(frame, changed ? "Checked in. " + r.fullName() + " is now version " + r.version() + "." : "Checked in. " + r.fullName() + " is available to others again.");
                reloadKeepSelection(r.id());
            }, e -> {
                h.idle();
                Toast.error(frame, ApiException.messageOf(e));
            });
            return false;
        });
    }

    private void discard(NodeInfo n) {
        Dialogs.confirm(frame, "Discard check-out?", n.fullName(),
                "Any changes in your working copy are thrown away and the file becomes available to others again.", "Discard", true,
                () -> Async.run(() -> api.discardCheckout(n.id()), r -> {
                    CheckoutStore.forget(n.id());
                    Toast.show(frame, "Check-out discarded. " + r.fullName() + " is available to others again.");
                    reloadKeepSelection(r.id());
                }, this::fail));
    }

    // ================================================================== updates

    private void checkForUpdate() {
        Async.run(() -> api.latestUpdate(AppConfig.APP_VERSION), info -> {
            if (info != null && info.available()) {
                Toast.show(frame, "A new version of PMIS Docket (" + info.version() + ") is available.", "Install", () -> installUpdate(info.version()));
            }
        }, e -> { });
    }

    private void installUpdate(String version) {
        Path target = AppConfig.tempDir().resolve("update").resolve("PMIS-Docket-Setup-" + version + ".exe");
        Toast.Progress p = Toast.progress(frame, "Downloading PMIS Docket " + version + "…");
        p.bar().setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        Async.run(() -> {
            api.downloadUpdate(target);
            return target;
        }, f -> {
            p.close();
            try {
                new ProcessBuilder(f.toString(), "/SILENT", "/CLOSEAPPLICATIONS", "/RESTARTAPPLICATIONS").start();
                Platform.exit();
            } catch (Exception ex) {
                Toast.show(frame, "Couldn’t start the installer. It was saved to " + f);
            }
        }, e -> {
            p.close();
            fail(e);
        });
    }

    // ================================================================== input

    private void onKey(KeyEvent e) {
        boolean typing = e.getTarget() instanceof TextInputControl;
        if (e.getCode() == KeyCode.F && e.isShortcutDown()) {
            search.requestFocus();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.F5) {
            refresh();
            e.consume();
            return;
        }
        if (typing) {
            if (e.getCode() == KeyCode.ESCAPE) {
                search.clear();
                contentScroll.requestFocus();
            }
            return;
        }
        if (e.isAltDown() && e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.BACK_SPACE) backBtn.fire(false);
        else if (e.isAltDown() && e.getCode() == KeyCode.RIGHT) fwdBtn.fire(false);
        else if (e.isAltDown() && e.getCode() == KeyCode.UP) upBtn.fire(false);
        else if (e.isAltDown() && e.getCode() == KeyCode.ENTER) propsBtn.fire(true);
        else if (e.getCode() == KeyCode.ENTER && selected != null) open(selected);
        else if (e.getCode() == KeyCode.DELETE) deleteBtn.fire(true);
        else if (e.getCode() == KeyCode.F2) renameBtn.fire(true);
        else if (e.isShortcutDown() && e.isShiftDown() && e.getCode() == KeyCode.N) newBtn.fire(true);
        else if (e.isShortcutDown() && e.getCode() == KeyCode.U) uploadBtn.fire(true);
        else if (e.isShortcutDown() && e.getCode() == KeyCode.D) downloadBtn.fire(true);
        else if (e.isShortcutDown() && e.getCode() == KeyCode.X) cutBtn.fire(true);
        else if (e.isShortcutDown() && e.getCode() == KeyCode.C) copyBtn.fire(true);
        else if (e.isShortcutDown() && e.getCode() == KeyCode.V) pasteBtn.fire(true);
        else if (e.isShortcutDown() && e.getCode() == KeyCode.E) checkBtn.fire(true);
        else return;
        e.consume();
    }

    private void installDragAndDrop() {
        contentCard.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles() && canWriteHere()) {
                e.acceptTransferModes(TransferMode.COPY);
                if (!dropHint.isVisible()) {
                    dropHint.setVisible(true);
                    Anim.fadeIn(dropHint, 150);
                }
            }
            e.consume();
        });
        contentCard.setOnDragExited(e -> dropHint.setVisible(false));
        contentCard.setOnDragDropped(e -> {
            dropHint.setVisible(false);
            Dragboard db = e.getDragboard();
            boolean ok = db.hasFiles() && canWriteHere();
            if (ok) uploadFiles(db.getFiles());
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    // ================================================================== helpers

    private void fail(Throwable e) {
        if (e instanceof ApiException a && a.isUnauthorized()) {
            Toast.error(frame, a.getMessage());
            onSignOut.run();
            return;
        }
        Dialogs.message(frame, "Something went wrong", ApiException.messageOf(e), true);
    }

    private static String nullTo(String s, String fallback) { return s == null ? fallback : s; }

    private static Path uniquePath(Path dir, String fileName) {
        Path p = dir.resolve(fileName);
        if (!Files.exists(p)) return p;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        for (int i = 2; ; i++) {
            p = dir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(p)) return p;
        }
    }

    private static void showInFolder(Path file) {
        try {
            new ProcessBuilder("explorer.exe", "/select,", file.toAbsolutePath().toString()).start();
        } catch (Exception ignored) {
            // Not on Windows or Explorer unavailable.
        }
    }

    private static String hostOf(String url) {
        try {
            URI u = URI.create(url);
            return u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (Exception e) {
            return url;
        }
    }
}
