package net.yudichev.googlephotosupload.ui;

import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* Summary:
 * Min-width, Max-width, and not-resizable are respected absolutely. These take precedence over all other constraints
 * total width of columns tries to be the width of the table. i.e. columns fill the table, but not past 100%.
 * <p>
 * Programmer can register "fixed width" columns so that they tend not to change size when the table is resized but the user can directly resize them
 * <p>
 * When the entire table's size is changed, the excess space is shared on a ratio basis between non-fixed-width, resizable columns
 * When the user manually resizes columns, use that new size as the pref width for the columns
 * <p>
 * If a single column is manually resized, the delta is shared on a ratio basis between non-fixed-width columns, but only columns the resized column's right
 * side
 * <p>
 * If all of the columns are set to fixed-size or no-resize, extra space is given to / taken from all fixed width columns proportionally
 * <p>
 * If all of the columns are no-resize, then the columns will not fill the table's width exactly (except, of course, at one coincidental size).
 * <p>
 * Finally, following the guidelines above, extra space is given to columns that aren't at their pref width and need that type of space
 * (negative or positive) on a proportional basis first.
 * <p> Courtesy of https://github.com/JoshuaD84/HypnosMusicPlayer, thanks!
 */

@SuppressWarnings("rawtypes")
public class HypnosResizePolicy implements Callback<TableView.ResizeFeatures, Boolean> {

    private final List<TableColumn<?, ?>> fixedWidthColumns = new ArrayList<>();

    public void registerFixedWidthColumns(TableColumn<?, ?>... addMe) {
        fixedWidthColumns.addAll(Arrays.asList(addMe));
    }

    @SuppressWarnings({"BreakStatement", "MethodWithMoreThanThreeNegations", "OverlyComplexMethod", "OverlyLongMethod"}) // copied code
    @Override
    public Boolean call(TableView.ResizeFeatures feature) {

        var table = feature.getTable();

        if (table == null || table.getWidth() == 0) {
            return false;
        }

        var columnToResize = feature.getColumn();
        @SuppressWarnings("unchecked")
        List<? extends TableColumn<?, ?>> columns = table.getVisibleLeafColumns();

        //There seem to be two modes: Either the entire table is resized, or the user is resizing a single column
        //This boolean will soon tell us know which mode we're in.
        var singleColumnResizeMode = false;

        double targetDelta = feature.getDelta();

        if (columnToResize != null && columnToResize.isResizable()) {
            //Now we know we're in the mode where a single column is being resized.
            singleColumnResizeMode = true;

            //We want to grow that column by targetDelta, but making sure we stay within the bounds of min and max.
            var targetWidth = columnToResize.getWidth() + feature.getDelta();
            if (targetWidth >= columnToResize.getMaxWidth()) {
                targetWidth = columnToResize.getMaxWidth();
            } else if (targetWidth <= columnToResize.getMinWidth()) {
                targetWidth = columnToResize.getMinWidth();
            }
            targetDelta = targetWidth - columnToResize.getWidth();
        }

        var spaceToDistribute = calculateSpaceAvailable(table) - targetDelta;

        if (Math.abs(spaceToDistribute) < 1) {
            return false;
        }

        //First we try to distribute the space to columns that aren't at their pref width
        //but always obeying not-resizable, min-width, and max-width
        //The space is distributed on a ratio basis, so columns that want to be bigger grow faster, etc.

        List<TableColumn> columnsNotAtPref = new ArrayList<>();

        for (TableColumn column : columns) {
            var resizeThisColumn = false;

            //Choose to resize columns that aren't at their pref width and need the type of space we have (negative or positive) to get there
            //We do this pref - width > 1 thing instead of pref > width because very small differences don't matter
            //but they cause a bug where the column widths jump around wildly.
            if (spaceToDistribute > 0 && column.getPrefWidth() - column.getWidth() > 1) {
                resizeThisColumn = true;
            }
            if (spaceToDistribute < 0 && column.getWidth() - column.getPrefWidth() > 1) {
                resizeThisColumn = true;
            }

            //but never resize columns that aren't resizable, are the current manul resizing column
            //or are to the left of the current manual resize column
            if (!column.isResizable()) {
                resizeThisColumn = false;
            }
            if (singleColumnResizeMode && column == columnToResize) {
                resizeThisColumn = false;
            }
            if (singleColumnResizeMode && columns.indexOf(column) < columns.indexOf(columnToResize)) {
                resizeThisColumn = false;
            }

            if (resizeThisColumn) {
                columnsNotAtPref.add(column);
            }
        }

        distributeSpaceRatioToPref(columnsNotAtPref, spaceToDistribute);


        //See how much space we have left after distributing to satisfy preferences.
        spaceToDistribute = calculateSpaceAvailable(table) - targetDelta;

        if (Math.abs(spaceToDistribute) >= 1) {

            //Now we distribute remaining space across the rest of the columns, excluding as follows:

            List<TableColumn> columnsToReceiveSpace = new ArrayList<>();
            for (TableColumn column : columns) {
                var resizeThisColumn = true;

                //Never resize columns that aren't resizable
                if (!column.isResizable()) {
                    resizeThisColumn = false;
                }

                //Never make columns more narrow than their min width
                if (spaceToDistribute < 0 && column.getWidth() <= column.getMinWidth()) {
                    resizeThisColumn = false;
                }

                //Never make columns wider than their max width
                if (spaceToDistribute > 0 && column.getWidth() >= column.getMaxWidth()) {
                    resizeThisColumn = false;
                }

                //If the extra space is the result of an individual column being resized, don't include that column
                //when distributing the extra space
                if (singleColumnResizeMode && column == columnToResize) {
                    resizeThisColumn = false;
                }

                //Exclude fixed-width columns, for now. We may add them back in later if needed.
                if (fixedWidthColumns.contains(column)) {
                    resizeThisColumn = false;
                }

                //Exclude columns to the left of the resized column in the case of a single column manual resize
                if (singleColumnResizeMode && columns.indexOf(column) < columns.indexOf(columnToResize)) {
                    resizeThisColumn = false;
                }

                if (resizeThisColumn) {
                    columnsToReceiveSpace.add(column);
                }
            }

            if (columnsToReceiveSpace.isEmpty()) {
                if (singleColumnResizeMode) {
                    //If there are no eligible columns and we're doing a manual resize, we can break our fixed-width exclusion
                    // and distribute the space to only the first fixed-width column to the right, this time allowing fixedWidth columns to be changed.

                    for (var k = columns.indexOf(columnToResize) + 1; k < columns.size(); k++) {
                        TableColumn candidate = columns.get(k);
                        var resizeThisColumn = true;

                        //Never resize columns that aren't resizable
                        if (!candidate.isResizable()) {
                            resizeThisColumn = false;
                        }

                        //Never make columns more narrow than their min width
                        if (spaceToDistribute < 0 && candidate.getWidth() <= candidate.getMinWidth()) {
                            resizeThisColumn = false;
                        }

                        //Never make columns wider than their max width
                        if (spaceToDistribute > 0 && candidate.getWidth() >= candidate.getMaxWidth()) {
                            resizeThisColumn = false;
                        }

                        if (resizeThisColumn) {
                            columnsToReceiveSpace.add(candidate);
                            //We only want one column, so we break after we find one.
                            break;
                        }
                    }

                } else {
                    //If we're in full table resize mode and all of the columns are set to fixed-size or no-resize,
                    //extra space is given to / taken from all fixed width columns proportionally
                    for (TableColumn column : columns) {
                        if (fixedWidthColumns.contains(column) && column.isResizable()) {
                            columnsToReceiveSpace.add(column);
                        }
                    }
                }
            }

            //Now we distribute the space amongst all eligible columns. It is still possible for there to be no eligible columns, in that case, nothing happens.
            distributeSpaceRatio(columnsToReceiveSpace, spaceToDistribute);
        }

        if (singleColumnResizeMode) {
            //Now if the user is manually resizing one column, we adjust that column's width to include whatever space we made / destroyed
            //with the above operations.
            //I found it is better to do this at the end when the actual space create/destroyed is known, rather than doing at the top and then
            //trying to get that much space from the other columns. Sometimes the other columns resist, and this creates a much smoother user experience.
            setColumnWidth(columnToResize, columnToResize.getWidth() + calculateSpaceAvailable(table));

            //If it's a manual resize, set pref-widths to these user specified widths.
            //The user manually set them now, so they like this size. Try to respect them on next resize.
            for (TableColumn column : columns) {
                column.setPrefWidth(column.getWidth());
            }
        }

        return true;
    }

    private static void distributeSpaceRatioToPref(List<TableColumn> columns, double spaceToDistribute) {

        var spaceWanted = columns.stream().mapToDouble(column -> column.getPrefWidth() - column.getWidth()).sum();

        if (spaceWanted < spaceToDistribute) {
            for (var column : columns) {
                var targetWidth = column.getPrefWidth();
                if (targetWidth >= column.getMaxWidth()) {
                    targetWidth = column.getMaxWidth();
                } else if (targetWidth <= column.getMinWidth()) {
                    targetWidth = column.getMinWidth();
                }
                setColumnWidth(column, targetWidth);
            }
        } else {
            for (var column : columns) {
                var targetWidth = column.getWidth() + spaceToDistribute * (column.getPrefWidth() / spaceWanted);
                if (targetWidth >= column.getMaxWidth()) {
                    targetWidth = column.getMaxWidth();
                } else if (targetWidth <= column.getMinWidth()) {
                    targetWidth = column.getMinWidth();
                }
                setColumnWidth(column, targetWidth);
            }
        }
    }

    private static void distributeSpaceRatio(List<TableColumn> columns, double space) {

        var totalWidth = columns.stream().mapToDouble(TableColumnBase::getWidth).sum();

        for (var column : columns) {
            var targetWidth = column.getWidth() + space * (column.getWidth() / totalWidth);
            if (targetWidth >= column.getMaxWidth()) {
                targetWidth = column.getMaxWidth();
            } else if (targetWidth <= column.getMinWidth()) {
                targetWidth = column.getMinWidth();
            }
            setColumnWidth(column, targetWidth);
        }
    }

    private static void setColumnWidth(TableColumn column, double targetWidth) {
        try {
            var method = TableColumnBase.class.getDeclaredMethod("doSetWidth", double.class);
            method.setAccessible(true);
            method.invoke(column, targetWidth);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static double calculateSpaceAvailable(TableView<?> table) {
        ObservableList<? extends TableColumn<?, ?>> columns = table.getVisibleLeafColumns();
        var spaceToDistribute = table.getWidth() - getScrollbarWidth(table) - 4;

        //That -4 is annoying. I'm sure there's a way to actually get that value
        //See thread here: https://stackoverflow.com/questions/47852175/how-to-spread-the-total-width-amongst-all-columns-on-table-construction
        //Which is also implemented below in calculateSpaceAvailableNew
        //Unfortunately, that solution didn't work on manual resizes.

        for (var column : columns) {
            spaceToDistribute -= column.getWidth();
        }
        return spaceToDistribute;
    }

    private static double getScrollbarWidth(TableView table) {
        double scrollBarWidth = 0;
        var nodes = table.lookupAll(".scroll-bar");
        for (var node : nodes) {
            if (node instanceof ScrollBar) {
                var sb = (ScrollBar) node;
                if (sb.getOrientation() == Orientation.VERTICAL) {
                    if (sb.isVisible()) {
                        scrollBarWidth = sb.getWidth();
                    }
                }
            }
        }

        return scrollBarWidth;
    }
}