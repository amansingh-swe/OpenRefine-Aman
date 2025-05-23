/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.google.refine.operations.recon;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.google.refine.browsing.EngineConfig;
import com.google.refine.browsing.RowVisitor;
import com.google.refine.history.Change;
import com.google.refine.model.Cell;
import com.google.refine.model.Column;
import com.google.refine.model.ColumnsDiff;
import com.google.refine.model.Project;
import com.google.refine.model.Recon;
import com.google.refine.model.Recon.Judgment;
import com.google.refine.model.Row;
import com.google.refine.model.changes.CellChange;
import com.google.refine.model.changes.ReconChange;
import com.google.refine.model.recon.ReconConfig;
import com.google.refine.model.recon.StandardReconConfig;
import com.google.refine.operations.EngineDependentMassCellOperation;
import com.google.refine.operations.OperationDescription;

public class ReconMarkNewTopicsOperation extends EngineDependentMassCellOperation {

    final protected boolean _shareNewTopics;
    final protected String _service;
    final protected String _identifierSpace;
    final protected String _schemaSpace;

    @JsonCreator
    public ReconMarkNewTopicsOperation(
            @JsonProperty("engineConfig") EngineConfig engineConfig,
            @JsonProperty("columnName") String columnName,
            @JsonProperty("shareNewTopics") boolean shareNewTopics,
            @JsonProperty("service") String service,
            @JsonProperty("identifierSpace") String identifierSpace,
            @JsonProperty("schemaSpace") String schemaSpace) {
        super(engineConfig, columnName, false);
        _shareNewTopics = shareNewTopics;
        _service = service;
        _identifierSpace = identifierSpace;
        _schemaSpace = schemaSpace;
    }

    @JsonProperty("columnName")
    public String getColumnName() {
        return _columnName;
    }

    @JsonProperty("shareNewTopics")
    public boolean getShareNewTopics() {
        return _shareNewTopics;
    }

    @JsonProperty("service")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getService() {
        return _service;
    }

    @JsonProperty("identifierSpace")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getIdentifierSpace() {
        return _identifierSpace;
    }

    @JsonProperty("schemaSpace")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSchemaSpace() {
        return _schemaSpace;
    }

    @Override
    protected String getBriefDescription(Project project) {
        return _shareNewTopics ? OperationDescription.recon_mark_new_topics_shared_brief(_columnName)
                : OperationDescription.recon_mark_new_topics_brief(_columnName);
    }

    @Override
    protected String createDescription(Column column,
            List<CellChange> cellChanges) {
        return _shareNewTopics ? OperationDescription.recon_mark_new_topics_shared_desc(cellChanges.size(), column.getName())
                : OperationDescription.recon_mark_new_topics_desc(cellChanges.size(), column.getName());

    }

    @Override
    public Optional<Set<String>> getColumnDependenciesWithoutEngine() {
        return Optional.of(Set.of(_columnName));
    }

    @Override
    public Optional<ColumnsDiff> getColumnsDiff() {
        return Optional.of(ColumnsDiff.modifySingleColumn(_columnName));
    }

    @Override
    public ReconMarkNewTopicsOperation renameColumns(Map<String, String> newColumnNames) {
        return new ReconMarkNewTopicsOperation(
                _engineConfig.renameColumnDependencies(newColumnNames),
                newColumnNames.getOrDefault(_columnName, _columnName),
                _shareNewTopics,
                _service,
                _identifierSpace,
                _schemaSpace);
    }

    protected ReconConfig getNewReconConfig(Column column) {
        return column.getReconConfig() != null ? column.getReconConfig()
                : new StandardReconConfig(
                        _service,
                        _identifierSpace,
                        _schemaSpace,
                        null,
                        false,
                        Collections.emptyList(),
                        0);
    }

    @Override
    protected RowVisitor createRowVisitor(Project project, List<CellChange> cellChanges, long historyEntryID) throws Exception {
        Column column = project.columnModel.getColumnByName(_columnName);
        ReconConfig reconConfig = getNewReconConfig(column);

        return new RowVisitor() {

            int cellIndex;
            List<CellChange> cellChanges;
            Map<String, Recon> sharedRecons = new HashMap<String, Recon>();
            long historyEntryID;

            public RowVisitor init(int cellIndex, List<CellChange> cellChanges, long historyEntryID) {
                this.cellIndex = cellIndex;
                this.cellChanges = cellChanges;
                this.historyEntryID = historyEntryID;
                return this;
            }

            @Override
            public void start(Project project) {
                // nothing to do
            }

            @Override
            public void end(Project project) {
                // nothing to do
            }

            private Recon createNewRecon() {
                return reconConfig.createNewRecon(historyEntryID);
            }

            @Override
            public boolean visit(Project project, int rowIndex, Row row) {
                Cell cell = row.getCell(cellIndex);
                if (cell != null) {
                    Recon recon = null;
                    if (_shareNewTopics) {
                        String s = cell.value == null ? "" : cell.value.toString();
                        if (sharedRecons.containsKey(s)) {
                            recon = sharedRecons.get(s);
                            recon.judgmentBatchSize++;
                        } else {
                            recon = createNewRecon();
                            recon.judgment = Judgment.New;
                            recon.judgmentBatchSize = 1;
                            recon.judgmentAction = "mass";

                            sharedRecons.put(s, recon);
                        }
                    } else {
                        recon = cell.recon == null ? createNewRecon() : cell.recon.dup(historyEntryID);
                        recon.match = null;
                        recon.matchRank = -1;
                        recon.judgment = Judgment.New;
                        recon.judgmentBatchSize = 1;
                        recon.judgmentAction = "mass";
                    }

                    Cell newCell = new Cell(cell.value, recon);

                    CellChange cellChange = new CellChange(rowIndex, cellIndex, cell, newCell);
                    cellChanges.add(cellChange);
                }
                return false;
            }
        }.init(column.getCellIndex(), cellChanges, historyEntryID);
    }

    @Override
    protected Change createChange(Project project, Column column, List<CellChange> cellChanges) {
        return new ReconChange(
                cellChanges,
                _columnName,
                getNewReconConfig(column),
                null);
    }

}
