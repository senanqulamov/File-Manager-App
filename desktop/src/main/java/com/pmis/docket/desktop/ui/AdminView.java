package com.pmis.docket.desktop.ui;

import com.pmis.docket.desktop.AppConfig;
import com.pmis.docket.desktop.api.ApiClient;
import com.pmis.docket.desktop.api.ApiException;
import com.pmis.docket.desktop.api.Model;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/** IT admin console: users, folder permissions, access requests, audit log, server, Recycle Bin, app updates. */
public class AdminView {
    private final WindowFrame frame;
    private final ApiClient api;
    private final Model.UserInfo me;
    private final HBox root = new HBox(12);
    private final VBox nav = new VBox(2);
    private final StackPane content = new StackPane();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private String section = "Users";

    public AdminView(WindowFrame frame, ApiClient api, Model.UserInfo me) {
        this.frame = frame;
        this.api = api;
        this.me = me;
        build();
    }

    public Node root() { return root; }

    public void open(String which) {
        section = which;
        navButtons.forEach((k, b) -> {
            b.getStyleClass().remove("active");
            if (k.equals(which)) b.getStyleClass().add("active");
        });
        content.getChildren().setAll(loading());
        switch (which) {
            case "Folder permissions" -> permissions(null);
            case "Access requests" -> requests();
            case "Audit log" -> audit("All", "");
            case "Server" -> server();
            case "Recycle Bin" -> recycle();
            case "App updates" -> updates();
            default -> users();
        }
    }

    private void build() {
        Label mark = new Label();
        mark.setGraphic(Icons.of(Icons.SHIELD, 18));
        mark.getStyleClass().add("admin-mark");
        VBox t = new VBox(DocDialogs.label("Admin console", "strong"), DocDialogs.label("IT administrators only", "muted-small"));
        HBox head = new HBox(10, mark, t);
        head.setAlignment(Pos.CENTER_LEFT);
        head.setPadding(new Insets(0, 8, 14, 8));
        nav.getChildren().add(head);
        Object[][] items = {{"Users", Icons.PEOPLE}, {"Folder permissions", Icons.KEY}, {"Access requests", Icons.INBOX},
                {"Audit log", Icons.LOG}, {"Server", Icons.DISK}, {"Recycle Bin", Icons.TRASH}, {"App updates", Icons.UPLOAD}};
        for (Object[] it : items) {
            String name = (String) it[0];
            Label l = new Label(name);
            HBox.setHgrow(l, Priority.ALWAYS);
            l.setMaxWidth(Double.MAX_VALUE);
            HBox c = new HBox(10, Icons.of((String) it[1], 16), l);
            c.setAlignment(Pos.CENTER_LEFT);
            Button b = new Button();
            b.setGraphic(c);
            b.getStyleClass().add("side-item");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> open(name));
            navButtons.put(name, b);
            nav.getChildren().add(b);
        }
        Region g = new Region();
        VBox.setVgrow(g, Priority.ALWAYS);
        Label note = DocDialogs.label("Every change here applies immediately and is written to the audit log.", "callout-small");
        nav.getChildren().addAll(g, note);
        nav.getStyleClass().add("sidebar");
        StackPane navCard = new StackPane(nav);
        navCard.getStyleClass().add("card");
        navCard.setMinWidth(236);
        navCard.setMaxWidth(236);
        content.getStyleClass().add("card");
        HBox.setHgrow(content, Priority.ALWAYS);
        root.getChildren().addAll(navCard, content);
        root.setPadding(new Insets(0, 14, 0, 14));
        Async.run(api::adminRequests, list -> {
            long pending = list.stream().filter(r -> "PENDING".equals(r.status())).count();
            if (pending > 0) {
                Label badge = new Label(String.valueOf(pending));
                badge.getStyleClass().add("count-badge");
                ((HBox) navButtons.get("Access requests").getGraphic()).getChildren().add(badge);
            }
        }, e -> { });
    }

    private void show(String title, String subtitle, Node action, Node body) {
        Label h = new Label(title);
        h.getStyleClass().add("page-title");
        VBox titles = new VBox(3, h);
        if (subtitle != null) titles.getChildren().add(DocDialogs.label(subtitle, "muted"));
        Region g = new Region();
        HBox.setHgrow(g, Priority.ALWAYS);
        HBox head = new HBox(12, titles, g);
        head.setAlignment(Pos.BOTTOM_LEFT);
        if (action != null) head.getChildren().add(action);
        VBox page = new VBox(18, head, body);
        page.setPadding(new Insets(22, 24, 24, 24));
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        content.getChildren().setAll(scroll);
        Anim.fadeUp(page, 0);
    }

    // ================================================================== users

    private void users() {
        Async.run(api::adminUsers, list -> {
            VBox rows = new VBox(2);
            rows.getChildren().add(header(List.of("User", "Department", "Role", "Storage", "Status", "Last sign-in", ""), new double[]{-1, 120, 120, 170, 90, 150, 70}));
            int i = 0;
            for (Model.AdminUser u : list) {
                Label av = new Label(u.initials());
                av.getStyleClass().add("avatar-dark");
                VBox who = new VBox(DocDialogs.label(u.displayName(), "strong"), DocDialogs.label("PMIS\\" + u.login(), "muted-small"));
                HBox userCell = new HBox(10, av, who);
                userCell.setAlignment(Pos.CENTER_LEFT);
                ProgressBar bar = new ProgressBar(u.quotaBytes() == 0 ? 0 : (double) u.usedBytes() / u.quotaBytes());
                bar.getStyleClass().addAll("progress-line", (double) u.usedBytes() / Math.max(1, u.quotaBytes()) > 0.85 ? "progress-red" : "x");
                bar.setMaxWidth(Double.MAX_VALUE);
                VBox storage = new VBox(4, bar, DocDialogs.label(Format.size(u.usedBytes()) + " of " + Format.size(u.quotaBytes()), "muted-small"));
                Label status = new Label(u.active() ? "Active" : "Disabled");
                status.getStyleClass().add(u.active() ? "access-write" : "access-read");
                status.getStyleClass().add("access-chip");
                Button edit = new Button("Edit");
                edit.getStyleClass().addAll("btn", "btn-outline", "btn-small");
                edit.setOnAction(e -> editUser(u));
                HBox row = cells(new Node[]{userCell, DocDialogs.label(u.department() == null ? "—" : u.department(), "cell"),
                        DocDialogs.label("ADMIN".equals(u.role()) ? "IT administrator" : "User", "cell"), storage, status,
                        DocDialogs.label(u.lastSignIn() == null ? "Never" : Format.when(u.lastSignIn()), "cell"), edit}, new double[]{-1, 120, 120, 170, 90, 150, 70});
                if (!u.active()) row.setOpacity(0.6);
                rows.getChildren().add(row);
                Anim.fadeUp(row, Math.min(i++ * 20, 240));
            }
            Button add = new Button("Add user", Icons.of(Icons.PLUS, 15));
            add.getStyleClass().addAll("btn", "btn-primary");
            add.setOnAction(e -> addUser());
            show("Users", list.size() + " accounts. Each person gets a private folder on the server.", add, wide(rows));
        }, this::fail);
    }

    private void addUser() {
        TextField name = field("e.g. Nigar Mammadova");
        TextField login = field("e.g. n.mammadova");
        Chips dept = new Chips(List.of("Finance", "HR", "Legal", "Management", "Operations", "IT"), "Finance");
        Chips quota = new Chips(List.of("10 GB", "20 GB", "50 GB", "100 GB"), "20 GB");
        Chips role = new Chips(List.of("User", "IT administrator"), "User");
        name.textProperty().addListener((o, a, b) -> {
            if (login.getUserData() == null) login.setText(suggestLogin(b));
        });
        login.setOnKeyTyped(e -> login.setUserData("edited"));
        VBox body = new VBox(8, DocDialogs.label("Full name", "field-label"), name, DocDialogs.label("Username", "field-label"), login,
                DocDialogs.label("DEPARTMENT", "section-label"), dept, DocDialogs.label("STORAGE FOR MY FILES", "section-label"), quota,
                DocDialogs.label("ROLE", "section-label"), role,
                DocDialogs.label("A temporary password is created and shown once. They choose their own at first sign-in.", "muted"));
        Dialogs.show(frame, "Add user", "Creates a network account and a private folder on the server", body, "Add user", false, true, 520, h -> {
            Map<String, Object> req = new HashMap<>();
            req.put("displayName", name.getText());
            req.put("login", login.getText());
            req.put("department", dept.value());
            req.put("role", role.value().equals("IT administrator") ? "ADMIN" : "USER");
            req.put("quotaGb", Long.parseLong(quota.value().replace(" GB", "")));
            h.busy("Creating…");
            Async.run(() -> api.createUser(req), t -> {
                h.close();
                DocDialogs.tempPassword(frame, t);
                users();
            }, e -> {
                h.idle();
                Toast.error(frame, ApiException.messageOf(e));
            });
            return false;
        });
    }

    private void editUser(Model.AdminUser u) {
        Chips dept = new Chips(List.of("Finance", "HR", "Legal", "Management", "Operations", "IT"), u.department());
        String q = (u.quotaBytes() / (1024L * 1024 * 1024)) + " GB";
        List<String> quotas = new ArrayList<>(List.of("10 GB", "20 GB", "50 GB", "100 GB"));
        if (!quotas.contains(q)) quotas.add(0, q);
        Chips quota = new Chips(quotas, q);
        Chips role = new Chips(List.of("User", "IT administrator"), "ADMIN".equals(u.role()) ? "IT administrator" : "User");
        Check active = new Check("Account active (turn off to block sign-in and sign them out; files are kept)");
        active.setSelected(u.active());
        Button reset = new Button("Reset password", Icons.of(Icons.KEY, 14));
        reset.getStyleClass().addAll("btn", "btn-outline", "btn-small");
        reset.setOnAction(e -> Dialogs.confirm(frame, "Reset password?", u.displayName(),
                "A new temporary password will be created. Their current password stops working.", "Reset", false,
                () -> Async.run(() -> api.resetPassword(u.id()), t -> DocDialogs.tempPassword(frame, t), this::fail)));
        Button delete = new Button("Delete user…", Icons.of(Icons.TRASH, 14));
        delete.getStyleClass().addAll("btn", "btn-outline", "btn-small", "danger-text");
        Dialogs.Handle[] self = new Dialogs.Handle[1];
        delete.setOnAction(e -> {
            self[0].close();
            deleteUser(u);
        });
        delete.setDisable(u.id().equals(me.id()));
        HBox accountActions = new HBox(8, reset, delete);
        VBox body = new VBox(8, DocDialogs.label("DEPARTMENT", "section-label"), dept, DocDialogs.label("STORAGE FOR MY FILES", "section-label"), quota,
                DocDialogs.label("ROLE", "section-label"), role, active, DocDialogs.label("ACCOUNT", "section-label"), accountActions);
        self[0] = Dialogs.show(frame, "Edit user", u.displayName() + " · PMIS\\" + u.login(), body, "Save", false, true, 520, h -> {
            Map<String, Object> req = new HashMap<>();
            req.put("department", dept.value());
            req.put("role", role.value().equals("IT administrator") ? "ADMIN" : "USER");
            req.put("quotaGb", Long.parseLong(quota.value().replace(" GB", "")));
            req.put("active", active.isSelected());
            h.busy("Saving…");
            Async.run(() -> api.updateUser(u.id(), req), x -> {
                h.close();
                Toast.show(frame, "Saved changes for " + u.displayName() + ".");
                users();
            }, e -> {
                h.idle();
                Toast.error(frame, ApiException.messageOf(e));
            });
            return false;
        });
    }

    private void deleteUser(Model.AdminUser u) {
        Async.run(api::adminUsers, all -> {
            List<Model.AdminUser> others = all.stream().filter(x -> !x.id().equals(u.id()) && x.active()).toList();
            boolean hasFiles = u.usedBytes() > 0;
            Chips what = new Chips(List.of("Give their files to a colleague", "Delete their files"), "Give their files to a colleague");
            ComboBox<String> to = new ComboBox<>();
            Map<String, Long> ids = new LinkedHashMap<>();
            for (Model.AdminUser x : others) ids.put(x.displayName() + " (" + x.login() + ")", x.id());
            to.getItems().addAll(ids.keySet());
            to.setPromptText("Choose who receives the files");
            to.getStyleClass().add("combo");
            to.setMaxWidth(Double.MAX_VALUE);
            Label filesNote = DocDialogs.label(hasFiles ? "Their My files holds " + Format.size(u.usedBytes()) + "." : "Their My files is empty.", "muted");
            what.setOnChange(v -> {
                to.setVisible(v.startsWith("Give"));
                to.setManaged(v.startsWith("Give"));
            });
            Label warn = DocDialogs.label("This can’t be undone. They can no longer sign in, and their shares and folder permissions are removed. "
                    + "Everything they did stays in the audit log. To only block sign-in, turn off “Account active” instead.", "callout-warn");
            warn.setMaxWidth(Double.MAX_VALUE);
            VBox body = new VBox(10, filesNote);
            if (hasFiles) body.getChildren().addAll(DocDialogs.label("THEIR FILES", "section-label"), what, to);
            body.getChildren().add(warn);
            Dialogs.show(frame, "Delete user", u.displayName() + " · PMIS\\" + u.login(), body, "Delete user", true, true, 520, h -> {
                Long target = null;
                boolean deleteFiles = false;
                if (hasFiles) {
                    if (what.value().startsWith("Give")) {
                        if (to.getValue() == null) {
                            Toast.error(frame, "Choose who receives the files.");
                            return false;
                        }
                        target = ids.get(to.getValue());
                    } else deleteFiles = true;
                }
                Long t = target;
                boolean df = deleteFiles;
                h.busy("Deleting…");
                Async.run(() -> api.deleteUser(u.id(), t, df), m -> {
                    h.close();
                    Toast.show(frame, m.message());
                    users();
                }, e -> {
                    h.idle();
                    Toast.error(frame, ApiException.messageOf(e));
                });
                return false;
            });
        }, this::fail);
    }

    private static String suggestLogin(String name) {
        String[] p = name.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (p.length == 0 || p[0].isEmpty()) return "";
        String first = p[0].replaceAll("[^a-z]", "");
        String last = p.length > 1 ? p[p.length - 1].replaceAll("[^a-z]", "") : "";
        return last.isEmpty() ? first : (first.isEmpty() ? "" : first.charAt(0) + ".") + last;
    }

    // ================================================================== folder permissions

    private void permissions(Long select) {
        Async.run(api::adminFolders, folders -> {
            VBox tree = new VBox(2);
            tree.getStyleClass().add("perm-tree-list");
            VBox detailBox = new VBox();
            StackPane detail = new StackPane(detailBox);
            detail.setAlignment(Pos.TOP_LEFT);
            Map<Long, Button> btns = new HashMap<>();
            for (Model.Folder f : folders) {
                Label l = new Label(f.name());
                l.setMinWidth(0);
                l.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(l, Priority.ALWAYS);
                HBox c = new HBox(8, FileIcon.folder(18, false), l);
                if (!f.hasOwnPermissions()) {
                    Label inh = new Label("inherits");
                    inh.getStyleClass().add("muted-tiny");
                    inh.setMinWidth(Region.USE_PREF_SIZE);
                    c.getChildren().add(inh);
                }
                c.setAlignment(Pos.CENTER_LEFT);
                Button b = new Button();
                b.setGraphic(c);
                b.getStyleClass().addAll("side-item", "perm-item");
                b.setMaxWidth(Double.MAX_VALUE);
                b.setPadding(new Insets(0, 10, 0, 10 + f.depth() * 18));
                b.setOnAction(e -> {
                    btns.values().forEach(x -> x.getStyleClass().remove("active"));
                    b.getStyleClass().add("active");
                    loadAcl(f.id(), detail);
                });
                btns.put(f.id(), b);
                tree.getChildren().add(b);
            }
            ScrollPane treeScroll = new ScrollPane(tree);
            treeScroll.setFitToWidth(true);
            treeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            treeScroll.getStyleClass().addAll("content-scroll", "perm-tree");
            treeScroll.setMinWidth(260);
            treeScroll.setPrefWidth(260);
            treeScroll.setMaxWidth(260);
            ScrollPane detailScroll = new ScrollPane(detail);
            detailScroll.setFitToWidth(true);
            detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            detailScroll.getStyleClass().add("content-scroll");
            HBox.setHgrow(detailScroll, Priority.ALWAYS);
            HBox body = new HBox(18, treeScroll, detailScroll);
            showFixed("Folder permissions", "Pick a company folder, then choose what each group or person can do there.", body);
            Long first = select != null ? select : folders.isEmpty() ? null : folders.get(0).id();
            if (first != null && btns.containsKey(first)) btns.get(first).fire();
        }, this::fail);
    }

    /** A page whose body fills the card and scrolls inside its own areas (no page-level scrolling). */
    private void showFixed(String title, String subtitle, Region body) {
        Label h = new Label(title);
        h.getStyleClass().add("page-title");
        VBox head = new VBox(3, h, DocDialogs.label(subtitle, "muted"));
        VBox page = new VBox(18, head, body);
        VBox.setVgrow(body, Priority.ALWAYS);
        page.setPadding(new Insets(22, 24, 24, 24));
        content.getChildren().setAll(page);
        Anim.fadeUp(page, 0);
    }

    private void loadAcl(long nodeId, StackPane detail) {
        Async.run(() -> api.acl(nodeId), acl -> renderAcl(acl, detail), this::fail);
    }

    private void renderAcl(Model.Acl acl, StackPane detail) {
        VBox box = new VBox(12);
        box.getChildren().add(new VBox(2, DocDialogs.label(acl.name(), "section-title"), DocDialogs.label("\\\\SRV-FILES › " + acl.path(), "muted-small")));
        box.setMaxWidth(760);
        if (acl.inherited()) {
            box.getChildren().add(DocDialogs.label("Inherited from " + (acl.inheritedFrom() == null ? "the company default (all staff: read only)" : "“" + acl.inheritedFrom() + "”")
                    + ". Changing a level gives this folder its own permissions.", "callout"));
        }
        VBox lines = new VBox();
        lines.getStyleClass().add("table-box");
        for (Model.AclLine line : acl.lines()) {
            Label who = new Label(line.who());
            who.getStyleClass().add("strong");
            HBox.setHgrow(who, Priority.ALWAYS);
            who.setMaxWidth(Double.MAX_VALUE);
            who.setMinWidth(120);
            HBox seg = new HBox(2);
            seg.getStyleClass().add("segmented");
            seg.setMinWidth(Region.USE_PREF_SIZE);
            for (String[] lv : new String[][]{{"NONE", "No access"}, {"READ", "Read only"}, {"WRITE", "Read & write"}, {"FULL", "Full control"}}) {
                Button b = new Button(lv[1]);
                b.getStyleClass().add("seg");
                if (lv[0].equals(line.access())) b.getStyleClass().addAll("on", "seg-" + lv[0].toLowerCase(Locale.ROOT));
                b.setOnAction(e -> {
                    if (line.fixed()) {
                        Toast.show(frame, "IT administrators always have full control.");
                        return;
                    }
                    Async.run(() -> api.setAcl(acl.nodeId(), line.principal(), lv[0]), a -> {
                        Toast.show(frame, acl.name() + ": " + line.who() + " now has " + lv[1].toLowerCase(Locale.ROOT) + ".");
                        renderAcl(a, detail);
                    }, this::fail);
                });
                seg.getChildren().add(b);
            }
            HBox row = new HBox(10, Icons.of(Icons.PEOPLE, 15), who, seg);
            if (line.fixed()) row.getChildren().add(withClass(Icons.of(Icons.LOCK, 13), "muted-icon"));
            else {
                Button rm = new Button();
                rm.setGraphic(Icons.of(Icons.CLOSE, 12));
                rm.getStyleClass().addAll("tb", "tb-icon");
                rm.setTooltip(new Tooltip("Remove"));
                rm.setOnAction(e -> Async.run(() -> api.removeAcl(acl.nodeId(), line.principal()), a -> renderAcl(a, detail), this::fail));
                row.getChildren().add(rm);
            }
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("table-row");
            lines.getChildren().add(row);
        }
        box.getChildren().add(lines);
        if (!acl.addable().isEmpty()) {
            ComboBox<String> pick = new ComboBox<>();
            Map<String, String> byName = new LinkedHashMap<>();
            for (Model.AclLine a : acl.addable()) byName.put(a.who(), a.principal());
            pick.getItems().addAll(byName.keySet());
            pick.setPromptText("Add a group or person…");
            pick.getStyleClass().add("combo");
            Button add = new Button("Add with read only");
            add.getStyleClass().addAll("btn", "btn-outline", "btn-small");
            add.setOnAction(e -> {
                String who = pick.getValue();
                if (who == null) return;
                Async.run(() -> api.setAcl(acl.nodeId(), byName.get(who), "READ"), a -> renderAcl(a, detail), this::fail);
            });
            HBox addRow = new HBox(8, pick, add);
            addRow.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(addRow);
        }
        if (!acl.inherited()) {
            Button inherit = new Button("Use the parent folder’s permissions instead");
            inherit.getStyleClass().addAll("btn", "btn-outline", "btn-small");
            inherit.setOnAction(e -> Async.run(() -> api.inheritAcl(acl.nodeId()), a -> renderAcl(a, detail), this::fail));
            box.getChildren().add(inherit);
        }
        box.getChildren().add(DocDialogs.label("Changes apply immediately to everyone in the group and are written to the audit log.", "muted-small"));
        detail.getChildren().setAll(box);
        Anim.fadeIn(box, 180);
    }

    // ================================================================== access requests

    private void requests() {
        Async.run(api::adminRequests, list -> {
            VBox cards = new VBox(8);
            if (list.isEmpty()) cards.getChildren().add(DocDialogs.label("No requests right now.", "muted"));
            int i = 0;
            for (Model.RequestInfo r : list) {
                Label icon = new Label();
                icon.setGraphic(Icons.of(Icons.KEY, 17));
                icon.getStyleClass().add("req-icon");
                Label text = new Label(r.userName() + " asked for " + r.level().toLowerCase(Locale.ROOT) + " access to " + r.folder());
                text.getStyleClass().add("strong");
                text.setWrapText(true);
                VBox txt = new VBox(3, text, DocDialogs.label((r.note() == null || r.note().isBlank() ? "" : "“" + r.note() + "” · ") + Format.when(r.at()), "muted-small"));
                HBox.setHgrow(txt, Priority.ALWAYS);
                HBox card = new HBox(14, icon, txt);
                card.setAlignment(Pos.CENTER_LEFT);
                card.getStyleClass().add("req-card");
                if ("PENDING".equals(r.status())) {
                    Button deny = new Button("Deny");
                    deny.getStyleClass().addAll("btn", "btn-outline", "btn-small");
                    Button approve = new Button("Approve");
                    approve.getStyleClass().addAll("btn", "btn-primary", "btn-small");
                    deny.setOnAction(e -> decide(r, false));
                    approve.setOnAction(e -> decide(r, true));
                    card.getChildren().addAll(deny, approve);
                } else {
                    Label st = new Label("APPROVED".equals(r.status()) ? "Approved" : "Denied");
                    st.getStyleClass().add("APPROVED".equals(r.status()) ? "status-ok" : "status-bad");
                    card.getChildren().add(st);
                }
                cards.getChildren().add(card);
                Anim.fadeUp(card, Math.min(i++ * 30, 240));
            }
            show("Access requests", "People asked to open folders they can’t see. Approving gives that person access to that folder only.", null, cards);
        }, this::fail);
    }

    private void decide(Model.RequestInfo r, boolean approve) {
        Async.run(() -> api.decide(r.id(), approve), x -> {
            Toast.show(frame, approve ? "Approved. " + r.userName() + " can now open " + r.folder() + "." : "Denied.");
            requests();
        }, this::fail);
    }

    // ================================================================== audit

    private void audit(String kind, String q) {
        Async.run(() -> api.audit(kind, q), list -> {
            Chips kinds = new Chips(List.of("All", "Opened", "Edited", "Shared", "Deleted", "Permissions", "Sign-in"), kind);
            TextField search = field("Search people, actions, files, computers");
            search.setText(q);
            search.setPrefWidth(280);
            kinds.setOnChange(k -> audit(k, search.getText()));
            search.setOnAction(e -> audit(kinds.value(), search.getText()));
            Region g = new Region();
            HBox.setHgrow(g, Priority.ALWAYS);
            HBox filters = new HBox(10, kinds, g, search);
            filters.setAlignment(Pos.CENTER_LEFT);
            VBox rows = new VBox(1);
            double[] w = {120, 150, -1, 260, 110};
            rows.getChildren().add(header(List.of("Time", "User", "Action", "Item", "Computer"), w));
            int i = 0;
            for (Model.AuditEntry a : list) {
                Label k = new Label(a.kind());
                k.getStyleClass().addAll("kind-chip", "kind-" + a.kind().toLowerCase(Locale.ROOT).replace("-", ""));
                Label act = new Label(a.action());
                act.setWrapText(true);
                HBox action = new HBox(8, k, act);
                action.setAlignment(Pos.CENTER_LEFT);
                HBox row = cells(new Node[]{DocDialogs.label(Format.when(a.at()), "cell"), DocDialogs.label(a.user(), "strong-small"), action,
                        DocDialogs.label(a.item() == null ? "—" : a.item(), "cell"), DocDialogs.label(a.computer() == null ? "" : a.computer(), "cell")}, w);
                rows.getChildren().add(row);
                if (i < 40) Anim.fadeUp(row, Math.min(i++ * 15, 240));
            }
            Button export = new Button("Export CSV", Icons.of(Icons.DOWNLOAD, 15));
            export.getStyleClass().addAll("btn", "btn-outline");
            export.setOnAction(e -> {
                Path target = Path.of(System.getProperty("user.home"), "Downloads", "audit-log-" + java.time.LocalDate.now() + ".csv");
                Async.run(() -> api.exportAudit(kinds.value(), search.getText(), target), () -> Toast.show(frame, "Saved " + target.getFileName() + " to Downloads."), this::fail);
            });
            show("Audit log", list.size() + " entries · every open, change, share, delete, permission change and sign-in. Entries can’t be edited.", export,
                    new VBox(14, filters, wide(rows)));
        }, this::fail);
    }

    // ================================================================== server

    private void server() {
        Async.run(api::stats, s -> {
            FlowPane cards = new FlowPane(12, 12);
            double used = s.totalBytes() <= 0 ? 0 : (double) (s.totalBytes() - s.freeBytes()) / s.totalBytes();
            ProgressBar disk = new ProgressBar(used);
            disk.getStyleClass().add("progress-line");
            disk.setMaxWidth(Double.MAX_VALUE);
            cards.getChildren().addAll(
                    statCard("Disk", Format.size(s.totalBytes() - s.freeBytes()) + " of " + Format.size(s.totalBytes()), disk),
                    statCard("Files in Docket", Format.size(s.filesBytes()), DocDialogs.label("Recycle Bin: " + Format.size(s.recycleBytes()), "muted-small")),
                    statCard("Users", String.valueOf(s.users()), DocDialogs.label(s.activeToday() + " signed in today", "muted-small")),
                    statCard("Checked out", String.valueOf(s.checkedOut()), DocDialogs.label("files being edited now", "muted-small")),
                    statCard("Access requests", String.valueOf(s.pendingRequests()), DocDialogs.label("waiting for a decision", "muted-small")));
            VBox tools = new VBox(8,
                    toolRow("LibreOffice", s.libreOffice(), "Word, Excel and PowerPoint previews, conversions, signing and stamping of Office files."),
                    toolRow("FFmpeg", s.ffmpeg(), "Video and audio conversion."));
            tools.getStyleClass().add("table-box");
            Label storage = DocDialogs.label("Storage folder on the server: " + s.storageRoot(), "muted-small");
            show("Server", "\\\\SRV-FILES · PMIS Docket Server", null, new VBox(16, cards, DocDialogs.label("CONVERSION TOOLS", "section-label"), tools, storage));
        }, this::fail);
    }

    private Node statCard(String title, String big, Node extra) {
        Label t = DocDialogs.label(title, "section-label");
        Label b = new Label(big);
        b.getStyleClass().add("stat-big");
        VBox v = new VBox(8, t, b, extra);
        v.getStyleClass().add("stat-card");
        v.setPrefWidth(230);
        return v;
    }

    private Node toolRow(String name, boolean ok, String what) {
        Label status = new Label(ok ? "Installed" : "Not found");
        status.getStyleClass().addAll("access-chip", ok ? "access-write" : "access-none");
        VBox txt = new VBox(2, DocDialogs.label(name, "strong"), DocDialogs.label(what, "muted-small"));
        HBox.setHgrow(txt, Priority.ALWAYS);
        HBox row = new HBox(12, txt, status);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("table-row");
        return row;
    }

    // ================================================================== recycle bin

    private void recycle() {
        Async.run(api::recycleBin, list -> {
            VBox rows = new VBox(2);
            double[] w = {-1, 260, 150, 130, 90, 90};
            rows.getChildren().add(header(List.of("Name", "Was in", "Deleted by", "Deleted", "Size", ""), w));
            if (list.isEmpty()) rows.getChildren().add(DocDialogs.label("The Recycle Bin is empty.", "muted"));
            for (Model.RecycleItem r : list) {
                Button restore = new Button("Restore");
                restore.getStyleClass().addAll("btn", "btn-outline", "btn-small");
                restore.setOnAction(e -> Async.run(() -> api.adminRestore(r.id()), x -> {
                    Toast.show(frame, "Restored " + r.name() + ".");
                    recycle();
                }, this::fail));
                rows.getChildren().add(cells(new Node[]{DocDialogs.label(r.name(), "strong-small"), DocDialogs.label(r.location(), "cell"),
                        DocDialogs.label(r.deletedBy(), "cell"), DocDialogs.label(Format.when(r.deletedAt()), "cell"),
                        DocDialogs.label("File".equals(r.type()) ? Format.size(r.sizeBytes()) : "Folder", "cell"), restore}, w));
            }
            Button purge = new Button("Empty items older than 30 days");
            purge.getStyleClass().addAll("btn", "btn-outline");
            purge.setOnAction(e -> Dialogs.confirm(frame, "Empty the Recycle Bin?", null,
                    "Items deleted more than 30 days ago are removed for good, including their old versions. This can’t be undone.", "Empty", true,
                    () -> Async.run(api::purge, m -> {
                        Toast.show(frame, m.message());
                        recycle();
                    }, this::fail)));
            show("Recycle Bin", "Deleted items stay here for 30 days. People can undo their own deletes; IT can restore anything.", purge, wide(rows));
        }, this::fail);
    }

    // ================================================================== app updates

    private void updates() {
        Async.run(() -> api.latestUpdate("0"), info -> {
            Label current = DocDialogs.label("This app: version " + AppConfig.APP_VERSION + " · On the server: "
                    + (info.version() == null ? "no update published" : "version " + info.version()), "callout");
            TextField version = field("New version, e.g. 1.1.0");
            TextField notes = field("What changed (optional)");
            Label file = DocDialogs.label("No installer chosen", "muted");
            File[] chosen = new File[1];
            Button choose = new Button("Choose PMIS-Docket-Setup.exe…");
            choose.getStyleClass().addAll("btn", "btn-outline");
            choose.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Installer", "*.exe"));
                File f = fc.showOpenDialog(frame.stage());
                if (f != null) {
                    chosen[0] = f;
                    file.setText(f.getName() + " · " + Format.size(f.length()));
                }
            });
            Button publish = new Button("Publish update", Icons.of(Icons.UPLOAD, 15));
            publish.getStyleClass().addAll("btn", "btn-primary");
            publish.setOnAction(e -> {
                if (chosen[0] == null || version.getText().isBlank()) {
                    Toast.show(frame, "Choose the installer and type the version.");
                    return;
                }
                publish.setDisable(true);
                publish.setText("Uploading…");
                Async.run(() -> api.publishUpdate(version.getText().trim(), notes.getText(), chosen[0].toPath()), x -> {
                    Toast.show(frame, "Published version " + x.version() + ". PCs will offer it at the next sign-in.");
                    updates();
                }, ex -> {
                    publish.setDisable(false);
                    publish.setText("Publish update");
                    fail(ex);
                });
            });
            VBox body = new VBox(10, current, DocDialogs.label("PUBLISH A NEW VERSION", "section-label"), version, notes, new HBox(10, choose, file), publish,
                    DocDialogs.label("Build the installer with installer\\build-installer.ps1 (set the same version). Every PC checks at sign-in and offers to install it.", "muted-small"));
            body.setMaxWidth(620);
            show("App updates", "Send a new version of PMIS Docket to every PC.", null, body);
        }, this::fail);
    }

    // ================================================================== helpers

    private Node loading() {
        Label l = DocDialogs.label("Loading…", "muted");
        StackPane p = new StackPane(l);
        p.setPadding(new Insets(40));
        return p;
    }

    private TextField field(String prompt) {
        TextField t = new TextField();
        t.setPromptText(prompt);
        t.getStyleClass().add("field");
        return t;
    }

    private HBox header(List<String> names, double[] widths) {
        Node[] cells = new Node[names.size()];
        for (int i = 0; i < names.size(); i++) cells[i] = DocDialogs.label(names.get(i), "th");
        HBox h = cells(cells, widths);
        h.getStyleClass().setAll("list-header");
        return h;
    }

    private HBox cells(Node[] nodes, double[] widths) {
        HBox row = new HBox(12);
        for (int i = 0; i < nodes.length; i++) {
            Node n = nodes[i];
            if (widths[i] > 0) {
                HBox box = new HBox(n);
                box.setAlignment(Pos.CENTER_LEFT);
                box.setMinWidth(widths[i]);
                box.setPrefWidth(widths[i]);
                box.setMaxWidth(widths[i]);
                if (n instanceof Region r) {
                    r.setMaxWidth(widths[i]);
                    HBox.setHgrow(r, Priority.ALWAYS);
                }
                row.getChildren().add(box);
            } else {
                HBox box = new HBox(n);
                box.setAlignment(Pos.CENTER_LEFT);
                box.setMinWidth(160);
                HBox.setHgrow(box, Priority.ALWAYS);
                row.getChildren().add(box);
            }
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("admin-row");
        return row;
    }

    private Node wide(Node table) {
        ScrollPane sp = new ScrollPane(table);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("content-scroll");
        if (table instanceof Region r) r.setMinWidth(860);
        return sp;
    }

    private Node withClass(Node n, String cls) {
        n.getStyleClass().add(cls);
        return n;
    }

    private void fail(Throwable e) {
        Toast.error(frame, ApiException.messageOf(e));
        if (content.getChildren().size() == 1 && content.getChildren().get(0) instanceof StackPane) {
            content.getChildren().setAll(DocDialogs.label(ApiException.messageOf(e), "callout-danger"));
        }
    }
}
