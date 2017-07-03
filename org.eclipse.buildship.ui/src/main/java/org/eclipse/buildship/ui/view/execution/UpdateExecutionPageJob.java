/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Simon Scholz (vogella GmbH) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.ui.view.execution;

import java.util.Set;

import org.gradle.tooling.events.FailureResult;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.task.TaskOperationDescriptor;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

/**
 * Updates the duration of the registered {@link OperationItem} instances in the {@link ExecutionsView} in regular intervals.
 */
public final class UpdateExecutionPageJob extends Job {

    private static final int REPEAT_DELAY = 100;

    private final Set<OperationItem> operationItems;
    private final Set<OperationItem> removeableItems;

    private volatile boolean running;
    private final TreeViewer treeViewer;

    public UpdateExecutionPageJob(TreeViewer treeViewer) {
        super("Updating duration of non-finished operations");

        this.treeViewer = treeViewer;
        this.operationItems = Sets.newConcurrentHashSet();
        this.removeableItems = Sets.newConcurrentHashSet();
        this.running = true;
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        Display display = PlatformUI.getWorkbench().getDisplay();
        if (!display.isDisposed()) {
            display.syncExec(new Runnable() {

                @Override
                public void run() {
                    if (!UpdateExecutionPageJob.this.treeViewer.getControl().isDisposed()) {
                        ImmutableSet<OperationItem> running = ImmutableSet.copyOf(UpdateExecutionPageJob.this.operationItems);
                        ImmutableSet<OperationItem> removed = ImmutableSet.copyOf(UpdateExecutionPageJob.this.removeableItems);
                        for (OperationItem operationItem : running) {
                            UpdateExecutionPageJob.this.treeViewer.update(operationItem, null);
                        }

                        UpdateExecutionPageJob.this.operationItems.removeAll(removed);
                        UpdateExecutionPageJob.this.removeableItems.removeAll(removed);

                        UpdateExecutionPageJob.this.treeViewer.refresh(false);
                        for (OperationItem operationItem : running) {
                            if (shouldBeVisible(operationItem)) {
                                UpdateExecutionPageJob.this.treeViewer.expandToLevel(operationItem, 0);
                            }
                        }
                    }
                }

                private boolean shouldBeVisible(OperationItem operationItem) {
                    return isOnMax2ndLevel(operationItem) || isTaskOperation(operationItem) || isFailedOperation(operationItem);
                }

                private boolean isOnMax2ndLevel(OperationItem operationItem) {
                    int level = 2;
                    while(level >= 0) {
                        if (operationItem.getParent() == null) {
                            return true;
                        } else {
                            level--;
                            operationItem = operationItem.getParent();
                        }
                    }
                    return false;
                }

                private boolean isTaskOperation(OperationItem operationItem) {
                    return operationItem.getStartEvent().getDescriptor() instanceof TaskOperationDescriptor;
                }

                private boolean isFailedOperation(OperationItem operationItem) {
                    FinishEvent finishEvent = operationItem.getFinishEvent();
                    return finishEvent != null ? finishEvent.getResult() instanceof FailureResult : false;
                }
            });
        }
        // reschedule the job such that is runs again in repeatDelay ms
        schedule(REPEAT_DELAY);
        return Status.OK_STATUS;
    }



    public void addOperationItem(OperationItem operationItem) {
        Preconditions.checkNotNull(operationItem.getStartEvent());
        this.operationItems.add(operationItem);
    }

    public void removeOperationItem(OperationItem operationItem) {
        Preconditions.checkNotNull(operationItem.getStartEvent());
        this.removeableItems.add(operationItem);
    }

    @Override
    public boolean shouldSchedule() {
        return this.running;
    }

    public void stop() {
        this.running = false;
    }
}
