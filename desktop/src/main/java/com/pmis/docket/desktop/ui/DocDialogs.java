package com.pmis.docket.desktop.ui;

import com.pmis.docket.desktop.api.ApiClient;
import com.pmis.docket.desktop.api.ApiException;
import com.pmis.docket.desktop.api.Model;
import com.pmis.docket.desktop.api.Model.NodeInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Dialogs for document actions: Convert, Sign, Lock, Stamp, Share, Properties (with versions and activity), access and passwords. */
public final class DocDialogs {
    private DocDialogs() { }

    // ================================================================== convert

    public static void convert(WindowFrame frame, ApiClient api, NodeInfo n, Consumer<NodeInfo> done) {
        Async.run(() -> api.conversions(n.id()), c -> {
            if (c.formats().isEmpty()) {
                Dialogs.message(frame, "Convert", c.note() == null ? FileKinds.plural(n.ext()) + " can’t be converted." : c.note(), false);
                return;
            }
            Label to = label("CONVERT TO", "section-label");
            Chips chips = new Chips(c.formats(), c.formats().get(0));
            Label result = new Label();
            result.getStyleClass().add("strong");
            Label where = new Label(n.canWrite() || !n.isFile() ? "Saved in the same folder. The original stays as it is."
                    : "You can’t save in this folder, so the copy goes to My files.");
            where.getStyleClass().add("muted");
            where.setWrapText(true);
            chips.setOnChange(v -> result.setText("New file: " + n.name() + (v.equals("Images") ? " (pages).zip" : "." + ext(v))));
            chips.select(chips.value());
            VBox info = new VBox(8, result, where);
            info.getStyleClass().add("callout");
            if (c.note() != null) {
                Label note = label(c.note(), "muted-small");
                info.getChildren().add(note);
            }
            VBox body = new VBox(12, to, chips, info);
            Dialogs.show(frame, "Convert", n.fullName(), body, "Convert", false, true, 500, h -> {
                h.busy("Converting…");
                Async.run(() -> api.convert(n.id(), chips.value()), r -> {
                    h.close();
                    Toast.show(frame, r.message());
                    done.accept(r.node());
                }, e -> {
                    h.idle();
                    Toast.error(frame, ApiException.messageOf(e));
                });
                return false;
            });
        }, e -> Toast.error(frame, ApiException.messageOf(e)));
    }

    private static String ext(String format) {
        return switch (format) {
            case "PDF" -> "pdf";
            case "Word" -> "docx";
            case "Excel" -> "xlsx";
            case "PowerPoint" -> "pptx";
            case "CSV" -> "csv";
            case "Text" -> "txt";
            case "PNG" -> "png";
            case "JPG" -> "jpg";
            case "MP4" -> "mp4";
            case "MP3", "MP3 audio" -> "mp3";
            case "WAV" -> "wav";
            case "GIF" -> "gif";
            default -> "zip";
        };
    }

    // ================================================================== sign

    public static void sign(WindowFrame frame, ApiClient api, Model.UserInfo user, NodeInfo n, Consumer<NodeInfo> done) {
        Chips mode = new Chips(List.of("Draw", "Type"), "Draw");
        SignaturePad pad = new SignaturePad();
        Label typed = new Label(user.displayName());
        typed.getStyleClass().add("typed-signature");
        StackPane area = new StackPane(pad);
        area.getStyleClass().add("signature-area");
        area.setMinHeight(160);
        Button clear = new Button("Clear");
        clear.getStyleClass().addAll("btn", "btn-outline", "btn-small");
        clear.setOnAction(e -> pad.clear());
        mode.setOnChange(v -> {
            area.getChildren().setAll("Draw".equals(v) ? pad : typed);
            clear.setVisible("Draw".equals(v));
            Anim.fadeIn(area.getChildren().get(0), 180);
        });
        HBox modeRow = new HBox(10, mode, new Region(), clear);
        HBox.setHgrow(modeRow.getChildren().get(1), Priority.ALWAYS);
        modeRow.setAlignment(Pos.CENTER_LEFT);
        Chips place = new Chips(List.of("Last page", "First page", "Every page"), "Last page");
        boolean pdf = "pdf".equalsIgnoreCase(n.ext());
        Label note = label(pdf ? "Signed with your personal PMIS certificate. Saved as a new version; earlier versions stay available."
                : "Office files are signed as PDF: a signed copy “" + n.name() + " (signed).pdf” is saved next to the original.", "muted");
        note.setWrapText(true);
        VBox body = new VBox(12, modeRow, area, label("PLACE SIGNATURE", "section-label"), place, note);
        Dialogs.show(frame, "Sign document", n.fullName(), body, "Sign", false, true, 520, h -> {
            boolean draw = "Draw".equals(mode.value());
            if (draw && pad.isEmpty()) {
                Toast.show(frame, "Draw your signature first, or choose Type.");
                return false;
            }
            String placement = switch (place.value()) {
                case "First page" -> "first";
                case "Every page" -> "every";
                default -> "last";
            };
            String png = draw ? pad.toPngBase64() : null;
            h.busy("Signing…");
            Async.run(() -> api.sign(n.id(), draw ? "DRAW" : "TYPE", png, placement), r -> {
                h.close();
                Toast.show(frame, r.message());
                done.accept(r.node());
            }, e -> {
                h.idle();
                Toast.error(frame, ApiException.messageOf(e));
            });
            return false;
        });
    }

    // ================================================================== lock

    public static void lock(WindowFrame frame, ApiClient api, NodeInfo n, Consumer<NodeInfo> done) {
        PasswordField pass = new PasswordField();
        pass.getStyleClass().add("field");
        PasswordField again = new PasswordField();
        again.getStyleClass().add("field");
        HBox strength = new HBox(4);
        Region[] bars = new Region[4];
        for (int i = 0; i < 4; i++) {
            bars[i] = new Region();
            bars[i].getStyleClass().add("strength");
            HBox.setHgrow(bars[i], Priority.ALWAYS);
            bars[i].setMaxWidth(Double.MAX_VALUE);
            strength.getChildren().add(bars[i]);
        }
        Label strengthText = label("Use at least 6 characters. Longer is better.", "muted-small");
        pass.textProperty().addListener((o, a, b) -> {
            int score = score(b);
            for (int i = 0; i < 4; i++) {
                bars[i].getStyleClass().removeAll("weak", "ok", "strong");
                if (i < score) bars[i].getStyleClass().add(score <= 1 ? "weak" : score == 2 ? "ok" : "strong");
            }
            strengthText.setText(b.isEmpty() ? "Use at least 6 characters. Longer is better." : new String[]{"Too short", "Weak", "Fair", "Good", "Strong"}[score] + " password");
        });
        Check print = new Check("Allow printing");
        Check copy = new Check("Allow copying text");
        boolean inPlace = FileKinds.locksInPlace(n.ext());
        boolean isPdf = "pdf".equalsIgnoreCase(n.ext());
        VBox body = new VBox(10, label("Password", "field-label"), pass, label("Confirm password", "field-label"), again, strength, strengthText);
        if (isPdf) body.getChildren().addAll(print, copy);
        Label note = label(inPlace
                ? "The file is encrypted with AES-256 and saved as a new version. Docket does not keep the password — if it’s lost, the file can’t be opened."
                : FileKinds.plural(n.ext()) + " can’t hold a password themselves. Docket puts it in an AES-256 encrypted ZIP that replaces the original (the original goes to the Recycle Bin).", inPlace ? "callout" : "callout-warn");
        note.setWrapText(true);
        note.setMaxWidth(Double.MAX_VALUE);
        body.getChildren().add(note);
        Dialogs.show(frame, "Lock with password", n.fullName(), body, inPlace ? "Lock file" : "Create encrypted ZIP", false, true, 500, h -> {
            if (pass.getText().length() < 6) {
                Anim.shake(pass);
                Toast.show(frame, "Use a password with at least 6 characters.");
                return false;
            }
            if (!pass.getText().equals(again.getText())) {
                Anim.shake(again);
                Toast.show(frame, "The two passwords are different.");
                return false;
            }
            h.busy("Locking…");
            Async.run(() -> api.lock(n.id(), pass.getText(), print.isSelected(), copy.isSelected()), r -> {
                h.close();
                Toast.show(frame, r.message());
                done.accept(r.node());
            }, e -> {
                h.idle();
                Toast.error(frame, ApiException.messageOf(e));
            });
            return false;
        });
    }

    // ================================================================== unlock

    public static void unlock(WindowFrame frame, ApiClient api, NodeInfo n, Consumer<NodeInfo> done) {
        PasswordField pass = new PasswordField();
        pass.getStyleClass().add("field");
        boolean zip = "zip".equalsIgnoreCase(n.ext());
        Label note = label(zip ? "The files inside the encrypted ZIP are put back in this folder without a password. The ZIP goes to the Recycle Bin."
                : "The password is removed and the file is saved as a new version. The locked version stays in the history.", "callout");
        note.setMaxWidth(Double.MAX_VALUE);
        VBox body = new VBox(10, label("Password", "field-label"), pass, note);
        Dialogs.show(frame, "Unlock", n.fullName(), body, "Unlock", false, true, 480, h -> {
            if (pass.getText().isEmpty()) {
                Anim.shake(pass);
                return false;
            }
            h.busy("Unlocking…");
            Async.run(() -> api.unlock(n.id(), pass.getText()), r -> {
                h.close();
                Toast.show(frame, r.message());
                done.accept(r.node());
            }, e -> {
                h.idle();
                Anim.shake(pass);
                Toast.error(frame, ApiException.messageOf(e));
            });
            return false;
        });
    }

    private static int score(String p) {
        if (p == null || p.length() < 6) return 0;
        int s = 1;
        if (p.length() >= 10) s++;
        if (p.matches(".*[A-Z].*") && p.matches(".*[a-z].*")) s++;
        if (p.matches(".*[0-9].*") && p.matches(".*[^A-Za-z0-9].*")) s++;
        return Math.min(4, s);
    }

    // ================================================================== stamp

    private static final Map<String, String> STAMP_COLORS = Map.of("APPROVED", "#15803D", "CONFIDENTIAL", "#C2410C", "DRAFT", "#6B7280",
            "COPY", "#2563EB", "PAID", "#15803D", "REJECTED", "#B42318");

    public static void stamp(WindowFrame frame, ApiClient api, NodeInfo n, Consumer<NodeInfo> done) {
        Chips chips = new Chips(List.of("APPROVED", "CONFIDENTIAL", "DRAFT", "COPY", "PAID", "REJECTED"), "APPROVED");
        TextField custom = new TextField();
        custom.setPromptText("Or type your own text, e.g. FOR REVIEW");
        custom.getStyleClass().add("field");
        Label preview = new Label("APPROVED");
        preview.getStyleClass().add("stamp-preview");
        StackPane previewBox = new StackPane(preview);
        previewBox.getStyleClass().add("stamp-box");
        Runnable update = () -> {
            String t = custom.getText() == null || custom.getText().isBlank() ? chips.value() : custom.getText().trim().toUpperCase(Locale.ROOT);
            String c = STAMP_COLORS.getOrDefault(t, "#C2410C");
            preview.setText(t);
            preview.setStyle("-fx-text-fill: " + c + "; -fx-border-color: " + c + ";");
        };
        chips.setOnChange(v -> {
            custom.clear();
            update.run();
        });
        custom.textProperty().addListener((o, a, b) -> update.run());
        update.run();
        String e = n.ext() == null ? "" : n.ext().toLowerCase(Locale.ROOT);
        boolean copyMode = !(e.equals("pdf") || FileKinds.of(e).kind() == FileKinds.Kind.IMAGE);
        Label note = label(copyMode ? "Word and PowerPoint files get a stamped PDF copy next to the original."
                : "The stamp is added to " + (e.equals("pdf") ? "every page" : "the picture") + " and saved as a new version.", "muted");
        note.setWrapText(true);
        VBox body = new VBox(12, chips, custom, previewBox, note);
        Dialogs.show(frame, "Stamp", n.fullName(), body, "Apply stamp", false, true, 520, h -> {
            String text = preview.getText();
            h.busy("Stamping…");
            Async.run(() -> api.stamp(n.id(), text, STAMP_COLORS.get(text)), r -> {
                h.close();
                Toast.show(frame, r.message());
                done.accept(r.node());
            }, ex -> {
                h.idle();
                Toast.error(frame, ApiException.messageOf(ex));
            });
            return false;
        });
    }

    // ================================================================== share

    public static void share(WindowFrame frame, ApiClient api, NodeInfo n, Runnable done) {
        Async.run(() -> new Object[]{api.people(), api.shares(n.id())}, r -> {
            @SuppressWarnings("unchecked") List<Model.Person> people = (List<Model.Person>) r[0];
            @SuppressWarnings("unchecked") List<Model.ShareInfo> existing = (List<Model.ShareInfo>) r[1];
            Set<Long> already = new HashSet<>();
            for (Model.ShareInfo s : existing) already.add(s.userId());
            VBox list = new VBox(2);
            list.getStyleClass().add("pick-list");
            Map<Long, Check> picks = new LinkedHashMap<>();
            for (Model.Person p : people) {
                Check cb = new Check(null);
                Label av = new Label(p.initials());
                av.getStyleClass().add("avatar-purple");
                Label name = new Label(p.name());
                name.getStyleClass().add("strong");
                Label dept = label(p.department() == null ? "" : p.department(), "muted-small");
                VBox txt = new VBox(name, dept);
                Region g = new Region();
                HBox.setHgrow(g, Priority.ALWAYS);
                Label tag = label(already.contains(p.id()) ? "Already shared" : "", "tag-purple");
                HBox row = new HBox(10, cb, av, txt, g, tag);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("pick-row");
                row.setOnMouseClicked(e -> cb.setSelected(!cb.isSelected()));
                picks.put(p.id(), cb);
                list.getChildren().add(row);
            }
            ScrollPane scroll = new ScrollPane(list);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(210);
            scroll.getStyleClass().add("pick-scroll");
            Chips level = new Chips(List.of("Can view", "Can edit"), "Can view");
            Chips expiry = new Chips(List.of("1 day", "7 days", "30 days", "No expiry"), "7 days");
            Label note = label("They’ll find it under “Shared with me”. The file stays in your folder. You can stop sharing any time in Properties › Sharing.", "muted");
            note.setWrapText(true);
            VBox body = new VBox(10, label("PEOPLE", "section-label"), scroll, label("THEY CAN", "section-label"), level,
                    label("ACCESS ENDS", "section-label"), expiry, note);
            Dialogs.show(frame, "Share", n.fullName(), body, "Share", false, true, 540, h -> {
                List<Long> ids = new ArrayList<>();
                picks.forEach((id, cb) -> { if (cb.isSelected()) ids.add(id); });
                if (ids.isEmpty()) {
                    Toast.show(frame, "Choose at least one colleague.");
                    return false;
                }
                Integer days = switch (expiry.value()) {
                    case "1 day" -> 1;
                    case "7 days" -> 7;
                    case "30 days" -> 30;
                    default -> null;
                };
                h.busy("Sharing…");
                Async.run(() -> api.share(n.id(), ids, "Can edit".equals(level.value()), days), s -> {
                    h.close();
                    Toast.show(frame, "Shared " + n.fullName() + " with " + ids.size() + (ids.size() == 1 ? " person." : " people."));
                    done.run();
                }, e -> {
                    h.idle();
                    Toast.error(frame, ApiException.messageOf(e));
                });
                return false;
            });
        }, e -> Toast.error(frame, ApiException.messageOf(e)));
    }

    // ================================================================== properties with tabs

    public static void properties(WindowFrame frame, ApiClient api, NodeInfo n, String startTab, Runnable changed,
                                  Consumer<Path> openVersionCopy) {
        Async.run(() -> {
            Model.Properties p = api.properties(n.id());
            List<Model.Activity> act = api.activity(n.id());
            List<Model.Version> vs = n.isFile() ? api.versions(n.id()) : List.of();
            List<Model.ShareInfo> sh = n.mine() && !n.isRoot() ? api.shares(n.id()) : List.of();
            return new Object[]{p, act, vs, sh};
        }, r -> {
            Model.Properties p = (Model.Properties) r[0];
            @SuppressWarnings("unchecked") List<Model.Activity> act = (List<Model.Activity>) r[1];
            @SuppressWarnings("unchecked") List<Model.Version> vs = (List<Model.Version>) r[2];
            @SuppressWarnings("unchecked") List<Model.ShareInfo> sh = (List<Model.ShareInfo>) r[3];
            List<String> tabs = new ArrayList<>(List.of("General"));
            if (n.isFile()) tabs.add("Versions");
            tabs.add("Sharing");
            tabs.add("Activity");
            Chips tabBar = new Chips(tabs, tabs.contains(startTab) ? startTab : "General");
            tabBar.getStyleClass().add("tabs");
            StackPane page = new StackPane();
            Dialogs.Handle[] handle = new Dialogs.Handle[1];
            tabBar.setOnChange(t -> {
                Node content = switch (t) {
                    case "Versions" -> versionsTab(frame, api, n, vs, () -> { handle[0].close(); changed.run(); }, openVersionCopy);
                    case "Sharing" -> sharingTab(frame, api, n, p, sh, () -> { handle[0].close(); changed.run(); });
                    case "Activity" -> activityTab(act);
                    default -> generalTab(p);
                };
                page.getChildren().setAll(content);
                Anim.fadeIn(content, 180);
            });
            VBox body = new VBox(14, tabBar, page);
            handle[0] = Dialogs.show(frame, "Properties", n.fullName(), body, "Close", false, false, 660, h -> true);
            tabBar.select(tabBar.value());
        }, e -> Toast.error(frame, ApiException.messageOf(e)));
    }

    private static Node generalTab(Model.Properties p) {
        NodeInfo n = p.node();
        GridPane grid = new GridPane();
        grid.getStyleClass().add("props-grid");
        int r = 0;
        r = row(grid, r, "Name", n.fullName());
        r = row(grid, r, "Type", n.isFile() ? FileKinds.of(n.ext()).label() : n.isRoot() ? "Location" : "File folder");
        r = row(grid, r, "Location", p.location());
        if (n.isFile()) r = row(grid, r, "Size", Format.size(n.sizeBytes()));
        if (p.contains() != null) r = row(grid, r, "Contains", p.contains());
        r = row(grid, r, "Modified", Format.when(n.modifiedAt()) + (n.modifiedByName() == null ? "" : " by " + n.modifiedByName()));
        r = row(grid, r, "Owner", p.owner());
        if (n.isFile()) r = row(grid, r, "Version", String.valueOf(n.version()));
        if (n.isFile()) r = row(grid, r, "Status", n.checkedOutByName() == null ? "Available" : (n.checkedOutByMe() ? "Checked out by you" : "Being edited by " + n.checkedOutByName()));
        if (n.isFile()) {
            List<String> prot = new ArrayList<>();
            if (n.signed()) prot.add("Signed");
            if (n.locked()) prot.add("Password-locked");
            if (n.stamp() != null) prot.add("Stamped “" + n.stamp() + "”");
            r = row(grid, r, "Protection", prot.isEmpty() ? "None" : String.join(" · ", prot));
        }
        row(grid, r, "Your access", n.isSharedToMe() ? n.shareLevel() + " (shared by " + n.sharedByName() + ")" : n.accessLabel());
        VBox access = table(p.access());
        Label note = label(n.isPersonal() ? "Only the owner and the people they share with can see this." : "Only IT administrators can change access to company folders.", "muted");
        return new VBox(12, grid, label("WHO CAN ACCESS", "section-label"), access, note);
    }

    private static Node versionsTab(WindowFrame frame, ApiClient api, NodeInfo n, List<Model.Version> vs, Runnable changed, Consumer<Path> openCopy) {
        VBox list = new VBox(2);
        for (Model.Version v : vs) {
            Region dot = new Region();
            dot.getStyleClass().addAll("dot", v.current() ? "dot-blue" : "dot-grey");
            Label title = new Label("Version " + v.number());
            title.getStyleClass().add("strong");
            HBox head = new HBox(8, title);
            head.setAlignment(Pos.CENTER_LEFT);
            if (v.current()) head.getChildren().add(label("CURRENT", "tag-blue"));
            VBox txt = new VBox(2, head, label(v.byName() + " · " + Format.when(v.at()) + " · " + Format.size(v.sizeBytes()), "muted-small"),
                    label(v.note() == null ? "" : v.note(), "small"));
            HBox.setHgrow(txt, Priority.ALWAYS);
            Button open = new Button("Open");
            open.getStyleClass().addAll("btn", "btn-outline", "btn-small");
            open.setOnAction(e -> {
                Path target = com.pmis.docket.desktop.AppConfig.tempDir().resolve("versions").resolve(n.id() + "-v" + v.number())
                        .resolve(n.name() + " (version " + v.number() + ")" + (n.ext() == null ? "" : "." + n.ext()));
                Toast.show(frame, "Opening version " + v.number() + " (read-only copy)…");
                Async.run(() -> {
                    api.download(n.id(), target, false, v.number());
                    return target;
                }, openCopy, ex -> Toast.error(frame, ApiException.messageOf(ex)));
            });
            HBox row = new HBox(12, dot, txt, open);
            if (!v.current()) {
                Button restore = new Button("Restore");
                restore.getStyleClass().addAll("btn", "btn-dark", "btn-small");
                restore.setDisable(!n.canWrite() || n.checkedOutByOther());
                restore.setOnAction(e -> Dialogs.confirm(frame, "Restore version " + v.number() + "?", n.fullName(),
                        "Version " + v.number() + " becomes the newest version. Nothing is overwritten: all versions stay in the history.", "Restore", false,
                        () -> Async.run(() -> api.restoreVersion(n.id(), v.number()), x -> {
                            Toast.show(frame, "Restored version " + v.number() + ". It is now version " + x.version() + ".");
                            changed.run();
                        }, ex -> Toast.error(frame, ApiException.messageOf(ex)))));
                row.getChildren().add(restore);
            }
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("version-row");
            list.getChildren().add(row);
        }
        Label note = label("Every save, check-in, signature, lock and stamp creates a new version. Restoring adds a new version on top.", "muted-small");
        note.setWrapText(true);
        return new VBox(8, list, note);
    }

    private static Node sharingTab(WindowFrame frame, ApiClient api, NodeInfo n, Model.Properties p, List<Model.ShareInfo> shares, Runnable changed) {
        VBox box = new VBox(10);
        if (n.isSharedToMe()) {
            box.getChildren().add(label("Shared with you by " + n.sharedByName() + " (" + n.shareLevel().toLowerCase(Locale.ROOT) + ").", "callout"));
        } else if (!n.isPersonal()) {
            box.getChildren().add(label("Company folders are shared through folder permissions set by IT.", "callout"));
            box.getChildren().add(table(p.access()));
        } else if (n.isRoot()) {
            box.getChildren().add(label("Your whole My files can’t be shared. Share a folder or a file instead.", "callout"));
        } else {
            if (shares.isEmpty()) box.getChildren().add(label("Not shared with anyone.", "muted"));
            VBox list = new VBox();
            list.getStyleClass().add("table-box");
            for (Model.ShareInfo s : shares) {
                Label av = new Label(s.initials());
                av.getStyleClass().add("avatar-purple");
                VBox txt = new VBox(label(s.userName(), "strong"), label(s.level() + " · " + (s.expiresAt() == null ? "no expiry" : "until " + Format.when(s.expiresAt())), "muted-small"));
                HBox.setHgrow(txt, Priority.ALWAYS);
                Button stop = new Button("Stop sharing");
                stop.getStyleClass().addAll("btn", "btn-outline", "btn-small", "danger-text");
                stop.setOnAction(e -> Async.run(() -> api.unshare(n.id(), s.id()), () -> {
                    Toast.show(frame, "Stopped sharing with " + s.userName() + ".");
                    changed.run();
                }, ex -> Toast.error(frame, ApiException.messageOf(ex))));
                HBox row = new HBox(10, av, txt, stop);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("table-row");
                list.getChildren().add(row);
            }
            if (!shares.isEmpty()) box.getChildren().add(list);
            Button add = new Button("Share with someone…", Icons.of(Icons.PEOPLE, 15));
            add.getStyleClass().addAll("btn", "btn-purple");
            add.setOnAction(e -> share(frame, api, n, changed));
            box.getChildren().add(add);
        }
        return box;
    }

    private static Node activityTab(List<Model.Activity> act) {
        VBox list = new VBox(2);
        if (act.isEmpty()) list.getChildren().add(label("No activity recorded yet.", "muted"));
        for (Model.Activity a : act) {
            Label when = label(Format.when(a.at()), "muted-small");
            when.setMinWidth(120);
            Label what = new Label(a.user() + " · " + a.action());
            what.setWrapText(true);
            HBox.setHgrow(what, Priority.ALWAYS);
            what.setMaxWidth(Double.MAX_VALUE);
            Label pc = label(a.computer() == null ? "" : a.computer(), "muted-small");
            HBox row = new HBox(10, when, what, pc);
            row.getStyleClass().add("activity-row");
            list.getChildren().add(row);
        }
        Label note = label("Recorded on the server. Nobody can edit or delete these entries.", "muted-small");
        return new VBox(8, list, note);
    }

    // ================================================================== access, passwords

    public static void requestAccess(WindowFrame frame, ApiClient api, long nodeId, String name, String serverMessage) {
        Label msg = new Label(serverMessage == null ? "You don’t have permission to open “" + name + "”." : serverMessage);
        msg.setWrapText(true);
        msg.getStyleClass().add("callout-danger");
        msg.setMaxWidth(Double.MAX_VALUE);
        Chips level = new Chips(List.of("Read only", "Read & write"), "Read only");
        TextField why = new TextField();
        why.setPromptText("Why do you need it? (optional)");
        why.getStyleClass().add("field");
        VBox body = new VBox(12, msg, label("ASK IT FOR", "section-label"), level, why);
        Dialogs.show(frame, "Access denied", name, body, "Request access", false, true, 480, h -> {
            h.busy("Sending…");
            Async.run(() -> api.requestAccess(nodeId, "Read & write".equals(level.value()) ? "WRITE" : "READ", why.getText()), () -> {
                h.close();
                Toast.show(frame, "Request sent to IT administrators. You’ll get access when they approve it.");
            }, e -> {
                h.idle();
                Toast.error(frame, ApiException.messageOf(e));
            });
            return false;
        });
    }

    public static void changePassword(WindowFrame frame, ApiClient api, boolean mandatory, Runnable done) {
        PasswordField old = new PasswordField();
        PasswordField nw = new PasswordField();
        PasswordField again = new PasswordField();
        for (PasswordField f : List.of(old, nw, again)) f.getStyleClass().add("field");
        Label intro = label(mandatory ? "IT gave you a temporary password. Choose your own before you continue." : "Choose a new password for your PMIS account.", "callout");
        intro.setWrapText(true);
        intro.setMaxWidth(Double.MAX_VALUE);
        VBox body = new VBox(8, intro, label(mandatory ? "Temporary password" : "Current password", "field-label"), old,
                label("New password (at least 8 characters)", "field-label"), nw, label("Confirm new password", "field-label"), again);
        Dialogs.show(frame, "Change password", null, body, "Change password", false, !mandatory, 460, h -> {
            if (nw.getText().length() < 8) {
                Anim.shake(nw);
                Toast.show(frame, "Use at least 8 characters.");
                return false;
            }
            if (!nw.getText().equals(again.getText())) {
                Anim.shake(again);
                Toast.show(frame, "The new passwords are different.");
                return false;
            }
            h.busy("Saving…");
            Async.run(() -> api.changePassword(old.getText(), nw.getText()), u -> {
                h.close();
                Toast.show(frame, "Your password was changed.");
                done.run();
            }, e -> {
                h.idle();
                Toast.error(frame, ApiException.messageOf(e));
            });
            return false;
        });
    }

    /** Shows a temporary password once, with a copy button. */
    public static void tempPassword(WindowFrame frame, Model.TempPassword t) {
        Label pwd = new Label(t.temporaryPassword());
        pwd.getStyleClass().add("temp-password");
        Button copy = new Button("Copy", Icons.of(Icons.COPY, 14));
        copy.getStyleClass().addAll("btn", "btn-outline", "btn-small");
        copy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(t.temporaryPassword());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            Toast.show(frame, "Copied.");
        });
        HBox row = new HBox(10, pwd, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        Label note = label("Give this to " + t.user().displayName() + " (username " + t.user().login() + "). It is shown only once. They must choose their own password at first sign-in.", "muted");
        note.setWrapText(true);
        Dialogs.show(frame, "Temporary password", t.user().displayName(), new VBox(12, row, note), "Done", false, false, 480, h -> true);
    }

    // ================================================================== small helpers

    static Label label(String text, String cls) {
        Label l = new Label(text);
        l.getStyleClass().add(cls);
        l.setWrapText(true);
        return l;
    }

    static VBox table(List<Model.AccessRow> rows) {
        VBox access = new VBox();
        access.getStyleClass().add("table-box");
        for (Model.AccessRow a : rows) {
            Label who = new Label(a.who());
            Region g = new Region();
            HBox.setHgrow(g, Priority.ALWAYS);
            Label lvl = new Label(a.level());
            lvl.getStyleClass().add("strong");
            HBox line = new HBox(who, g, lvl);
            line.getStyleClass().add("table-row");
            access.getChildren().add(line);
        }
        return access;
    }

    private static int row(GridPane g, int r, String k, String v) {
        Label key = new Label(k);
        key.getStyleClass().add("muted");
        Label val = new Label(v == null ? "—" : v);
        val.getStyleClass().add("strong");
        val.setWrapText(true);
        g.add(key, 0, r);
        g.add(val, 1, r);
        return r + 1;
    }

    static Insets pad(double v) { return new Insets(v); }
}
