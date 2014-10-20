package org.yousharp.cluster;

import static com.google.common.base.Preconditions.*;

import com.google.common.net.HostAndPort;
import org.yousharp.util.ClusterUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.exceptions.JedisClusterException;
import redis.clients.util.ClusterNodeInformation;

import java.util.List;

/**
 * Data migration: migrate slots from one node to another;
 *
 * @author: lingguo
 * @time: 2014/10/19 0:38
 */
public class MigrateData {

    /**
     * migrate these slots {@code slots} from src node {@code srcNodeInfo} to dest node {@code destNodeInfo};
     *
     * @param srcNodeInfo   source node that migrates from
     * @param destNodeInfo  dest node that migrates to
     * @param slotsToMigrate     the slots to migrate
     */
    public static void migrateSlots(final HostAndPort srcNodeInfo, final HostAndPort destNodeInfo, final int... slotsToMigrate) {
        checkNotNull(srcNodeInfo, "srcNodeInfo cannot be null.");
        checkNotNull(destNodeInfo, "destNodeInfo cannot be null.");
        checkArgument(slotsToMigrate != null && slotsToMigrate.length > 0, "slots size cannot be 0.");

        Jedis srcNode = new Jedis(srcNodeInfo.getHostText(), srcNodeInfo.getPort());
        String srcNodeId = ClusterUtil.getNodeId(srcNode.clusterNodes());
        Jedis destNode = new Jedis(destNodeInfo.getHostText(), destNodeInfo.getPort());
        String destNodeId = ClusterUtil.getNodeId(destNode.clusterNodes());

        /** migrate every slot from src node to dest node */
        for (int slot: slotsToMigrate) {
            srcNode.clusterSetSlotMigrating(slot, destNodeId);
            destNode.clusterSetSlotImporting(slot, srcNodeId);

            srcNode.clusterSetSlotNode(slot, destNodeId);
            destNode.clusterSetSlotNode(slot, destNodeId);
        }

        /** wait for slots migration done */
        ClusterUtil.waitForMigrationDone(srcNodeInfo);
    }

    /**
     * migrate {@code num} slots from {@code srcNodeInfo} to {@code destNodeInfo};
     * for example: if num = 200, it means to migrate 200 slots from srcNodeInfo to destNodeInfo.
     *
     * @param srcNodeInfo   source node
     * @param destNodeInfo  dest node
     * @param numToMigrate            number of slots to migrate
     */
    public static void migrate(final HostAndPort srcNodeInfo, final HostAndPort destNodeInfo, final int numToMigrate) {
        checkNotNull(srcNodeInfo);
        checkNotNull(destNodeInfo);
        checkArgument(numToMigrate > 0 && numToMigrate < JedisCluster.HASHSLOTS);

        Jedis srcNode = new Jedis(srcNodeInfo.getHostText(), srcNodeInfo.getPort());
        ClusterNodeInformation srcNodeSlotsInfo = ClusterUtil.getNodeSlotsInfo(srcNode, srcNodeInfo);
        List<Integer> slotsOfSrcNode = srcNodeSlotsInfo.getAvailableSlots();
        if (slotsOfSrcNode.size() < numToMigrate) {
            throw new JedisClusterException("cannot migrate, available slots: " + slotsOfSrcNode.size() +
                    ", numToMigrate: " + numToMigrate);
        }
        int[] slotsToMigrate = new int[numToMigrate];
        for (int i = 0; i < numToMigrate; i++) {
            slotsToMigrate[i] = slotsOfSrcNode.get(i);
        }
        migrateSlots(srcNodeInfo, destNodeInfo, slotsToMigrate);
    }
}
