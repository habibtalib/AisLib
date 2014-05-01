/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dma.ais.tracker;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dk.dma.ais.binary.SixbitException;
import dk.dma.ais.message.AisMessage;
import dk.dma.ais.message.AisMessage5;
import dk.dma.ais.message.AisMessageException;
import dk.dma.ais.message.AisPositionMessage;
import dk.dma.ais.message.IVesselPositionMessage;
import dk.dma.ais.packet.AisPacket;
import dk.dma.ais.packet.AisPacketReader;
import dk.dma.ais.packet.AisPacketStream;
import dk.dma.ais.packet.AisPacketStream.Subscription;
import dk.dma.enav.model.geometry.BoundingBox;
import dk.dma.enav.model.geometry.CoordinateSystem;
import dk.dma.enav.model.geometry.Position;
import dk.dma.enav.model.geometry.PositionTime;
import dk.dma.enav.util.function.Consumer;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;

/**
 * This class can process a finite stream of AisPackets, and build a scenario
 * consisting of all received targets and a history of their movements.
 *
 * @author Thomas Borg Salling
 */
public class ScenarioTracker implements Tracker {

    @Override
    public Subscription readFromStream(AisPacketStream stream) {
        return stream.subscribe(new Consumer<AisPacket>() {
            public void accept(AisPacket p) {
                update(p);
            }
        });
    }

    public void readFromPacketReader(AisPacketReader packetReader) throws IOException {
        packetReader.forEachRemaining(new Consumer<AisPacket>() {
            @Override
            public void accept(AisPacket p) {
                update(p);
            }
        });
    }

    /**
     * Get the Date of the first update in this scenario.
     * @return
     */
    public Date scenarioBegin() {
        Date scenarioBegin = null;
        Set<Map.Entry<Integer, Target>> entries = targets.entrySet();
        Iterator<Map.Entry<Integer, Target>> i = entries.iterator();
        while (i.hasNext()) {
            Target target = i.next().getValue();
            try {
                Date targetFirstUpdate = target.positionReports.firstKey();
                if (scenarioBegin == null || targetFirstUpdate.before(scenarioBegin)) {
                    scenarioBegin = targetFirstUpdate;
                }
            } catch (NoSuchElementException e) {
            }
        }
        return scenarioBegin;
    }

    /**
     * Get the Date of the last update in this scenario.
     * @return
     */
    public Date scenarioEnd() {
        Date scenarioEnd = null;
        Set<Map.Entry<Integer, Target>> entries = targets.entrySet();
        Iterator<Map.Entry<Integer, Target>> i = entries.iterator();
        while (i.hasNext()) {
            Target target = i.next().getValue();
            try {
                Date targetFirstUpdate = target.positionReports.lastKey();
                if (scenarioEnd == null || targetFirstUpdate.after(scenarioEnd)) {
                    scenarioEnd = targetFirstUpdate;
                }
            } catch (NoSuchElementException e) {
            }
        }
        return scenarioEnd;
    }

    /**
     * Get bounding box containing all movements in this scenario.
     * @return
     */
    public BoundingBox boundingBox() {
        return boundingBox;
    }

    /**
     * Return all targets involved in this scenario.
     * @return
     */
    public ImmutableSet<Target> getTargets() {
        return ImmutableSet.copyOf(targets.values());
    }

    /**
     * Return all targets involved in this scenario and with a known location (ie. located inside of the bounding box).
     * @return
     */
    public Set<Target> getTargetsHavingPositionUpdates() {
        return Sets.filter(getTargets(), new com.google.common.base.Predicate<Target>() {
            @Override
            public boolean apply(@Nullable Target target) {
                return target.hasPosition();
            }
        });
    }

    public void update(AisPacket p) {
        AisMessage message;
        try {
            message = p.getAisMessage();
            int mmsi = message.getUserId();
            Target target;
            if (! targets.containsKey(mmsi)) {
                target = new Target();
                targets.put(mmsi, target);
            } else {
                target = targets.get(mmsi);
            }
            if (message instanceof IVesselPositionMessage) {
                updateBoundingBox((IVesselPositionMessage) message);
            }
            target.update(p);
        } catch (AisMessageException | SixbitException e) {
            // fail silently on unparsable packets 
            //e.printStackTrace();
        }
    }

    public void tagTarget(int mmsi, Object tag) {
        targets.get(mmsi).setTag(tag);
    }

    private final Map<Integer, Target> targets = new TreeMap<>();

    private BoundingBox boundingBox;

    private void updateBoundingBox(IVesselPositionMessage positionMessage) {
        if (positionMessage.isPositionValid()) {
            Position position = positionMessage.getValidPosition();
            if (position != null) {
                if (boundingBox == null) {
                    boundingBox = BoundingBox.create(position, position, CoordinateSystem.CARTESIAN);
                } else {
                    boundingBox = boundingBox.include(BoundingBox.create(position, position, CoordinateSystem.CARTESIAN));
                }
            }
        }
    }

    private static String aisStringToJavaString(String aisString) {
        return aisString.replace('@',' ').trim();
    }

    public final class Target implements Cloneable {

        public Target() {
        }

        public String getName() {
            return StringUtils.isBlank(name) ? getMmsi() : name;
        }

        public String getMmsi() {
            return String.valueOf(mmsi);
        }

        public Set<PositionReport> getPositionReports() {
            return ImmutableSet.copyOf(positionReports.values());
        }

        public int getToBow() {
            return toBow;
        }

        public int getToStern() {
            return toStern;
        }

        public int getToPort() {
            return toPort;
        }

        public int getToStarboard() {
            return toStarboard;
        }

        private void setTag(Object tag) {
            tags.add(tag);
        }

        public boolean isTagged(Object tag) {
            return tags.contains(tag);
        }

        public boolean hasPosition() {
            return positionReports.size() > 0;
        }

        /** Return position at at time atTime - interpolate or dead reckon if no nearby report exists */
        public PositionReport getPositionReportAt(Date atTime) {
            PositionReport positionReportAt = positionReports.get(atTime);
            if (positionReportAt == null) {
                /* no position report at desired time - will estime using interpolation or dead reckoning */
                PositionReport pr1 = positionReports.lowerEntry(atTime).getValue();
                PositionReport pr2;
                Map.Entry<Date, PositionReport> higherEntry = positionReports.higherEntry(atTime);
                if (higherEntry != null) {
                    pr2 = higherEntry.getValue();
                    positionReportAt = new PositionReport(PositionTime.createInterpolated(pr1.getPositionTime(), pr2.getPositionTime(), atTime.getTime()), pr1.getCog(), pr1.getSog(), pr1.getHeading());
                } else {
                    positionReportAt = new PositionReport(PositionTime.createExtrapolated(pr1.getPositionTime(), pr1.getCog(), pr1.getSog(), atTime.getTime()), pr1.getCog(), pr1.getSog(), pr1.getHeading());
                }
            }
            return positionReportAt;
        }

        private void update(AisPacket p) {
            AisMessage message = p.tryGetAisMessage();
            checkOrSetMmsi(message);
            if (message instanceof AisPositionMessage) {
                AisPositionMessage positionMessage = (AisPositionMessage) message;
                if (positionMessage.isPositionValid()) {
                    final float lat = (float) positionMessage.getPos().getLatitudeDouble();
                    final float lon = (float) positionMessage.getPos().getLongitudeDouble();
                    final int hdg = positionMessage.getTrueHeading();
                    final float cog = positionMessage.getCog() / 10.0f;
                    final float sog = positionMessage.getSog() / 10.0f;
                    final long timestamp = p.getBestTimestamp();
                    positionReports.put(new Date(timestamp), new PositionReport(timestamp, lat,lon, cog, sog, hdg));
                }
            } else if (message instanceof AisMessage5) {
                AisMessage5 message5 = (AisMessage5) message;
                name = aisStringToJavaString(message5.getName());
                toBow = message5.getDimBow();
                toStern = message5.getDimStern();
                toPort = message5.getDimPort();
                toStarboard = message5.getDimStarboard();
            }
        }

        private void checkOrSetMmsi(AisMessage message) {
            final int msgMmsi = message.getUserId();
            if (mmsi < 0) {
                mmsi = msgMmsi;
            } else {
                if (mmsi != msgMmsi) {
                    throw new IllegalArgumentException("Message from mmsi " + msgMmsi + " cannot update target with mmsi " + mmsi);
                }
            }
        }

        private String name;
        private int mmsi=-1, toBow=-1, toStern=-1, toPort=-1, toStarboard=-1;

        private final Set<Object> tags = new HashSet<>();
        private final TreeMap<Date, PositionReport> positionReports = new TreeMap<>();

        public final class PositionReport {
            private PositionReport(PositionTime pt, float cog, float sog, int heading) {
                this.positionTime = pt;
                this.cog = cog;
                this.sog = sog;
                this.heading = heading;
            }

            private PositionReport(long timestamp, float latitude, float longitude, float cog, float sog, int heading) {
                this.positionTime = PositionTime.create(latitude, longitude, timestamp);
                this.cog = cog;
                this.sog = sog;
                this.heading = heading;
            }

            public PositionTime getPositionTime() {
                return positionTime;
            }

            public long getTimestamp() {
                return positionTime.getTime();
            }

            public double getLatitude() {
                return positionTime.getLatitude();
            }

            public double getLongitude() {
                return positionTime.getLongitude();
            }

            public float getCog() {
                return cog;
            }

            public float getSog() {
                return sog;
            }

            public int getHeading() {
                return heading;
            }

            private final PositionTime positionTime;
            private final float cog;
            private final float sog;
            private final int heading;
        }
    }
}