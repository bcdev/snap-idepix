/*
 * Copyright (c) 2021.  Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 *
 */

package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import java.util.List;
import java.util.Map;

/**
 * @author Marco Peters
 */
class IdentifiedPcs {
    final Map<Integer, List<Integer>> indexToPositions;
    final Map<Integer, List<Integer>> offsetAtPositions;

    public IdentifiedPcs(Map<Integer, List<Integer>> indexToPositions, Map<Integer, List<Integer>> offsetAtPositions) {
        this.indexToPositions = indexToPositions;
        this.offsetAtPositions = offsetAtPositions;
    }

}
