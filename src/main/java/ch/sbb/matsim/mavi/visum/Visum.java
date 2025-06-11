package ch.sbb.matsim.mavi.visum;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.SafeArray;
import com.jacob.com.Variant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Thin but <em>clean</em> façade around the Visum COM‑API.
 * <p>
 * Goal: shield the application from <strong>Dispatch</strong>/<strong>Variant</strong>/ActiveX noise while
 * exposing a tiny, fluent interface that can be unit‑tested without Visum.
 * <p>
 * Typical usage:
 * <pre>{@code
 * try (Visum visum = Visum.connect(15)) {
 *     visum.load("C:/networks/sbb.ver");
 *
 *     Table stopPoints = visum.table("StopPoints");
 *     log.info("{} stop points", stopPoints.size());
 *
 *     String[][] attrs = stopPoints.attrs("No", "XCoord", "YCoord", "Name");
 * }
 * }</pre>
 */
public class Visum implements VisumDataSource {

	private static final Logger log = LogManager.getLogger(Visum.class);

	private final ActiveXComponent client;

	private Visum(int version) {
		this.client = new ActiveXComponent("Visum.Visum." + version);
		log.debug("Instantiated Visum COM object version {}", version);
	}

	/** Connect to the installed Visum COM server (throws if version is missing). */
	public static Visum connect(int version) {
		return new Visum(version);
	}

	/** Load a *.ver* or *.net* file into the running Visum instance. */
	public void load(String path) {
		log.info("Loading Visum version file: {}", path);
		Dispatch.call(client, "LoadVersion", new Variant(path));
	}

	/** Release COM resources. */
	@Override public void close() {
		if (client != null) {
			log.debug("Releasing Visum COM resources");
			client.safeRelease();
		}
	}

	/** Fluent wrapper giving safe, typed access to any Visum table. */
	@Override
    public DataTable getTable(String name) {
		Objects.requireNonNull(name, "table name");
		if (name.equals("TransferWalkTimeStopAreas")) return new ComListTable("CreateStopTransferWalkTimeList", client);
		return new ComNetTable(name, client);
	}

	public static final class ComNetTable implements DataTable {
		private final Dispatch dispatch;

		private ComNetTable(String name, ActiveXComponent client) {
			Dispatch net = Dispatch.get(client, "Net").toDispatch();
            this.dispatch = Dispatch.get(net, name).toDispatch();
		}

		/** Number of active elements respecting current filters. */
		@Override
        public int size() {
			return Dispatch.call(dispatch, "CountActive").getInt();
		}

		/** Raw column pull, COM‑behaviour compatible (missing cells -> empty string). */
		@Override
        public String[][] attrs(String... names) {
			int n = size();
			String[][] out = new String[n][names.length];

			for (int col = 0; col < names.length; col++) {
				String att = names[col];
				try {
					SafeArray sa = Dispatch.call(dispatch, "GetMultiAttValues", att).toSafeArray();
					for (int row = 0; row < n; row++) {
						out[row][col] = sa.getString(row, 1);
					}
				} catch (Exception ex) {
					log.warn("Attribute '{}' not found – filling with blanks", att);
					for (int row = 0; row < n; row++) out[row][col] = "";
				}
			}
			return out;
		}

	}

	public static final class ComListTable implements DataTable {

		/** Underlying VISUM list COM object. */
		private final Dispatch dispatch;

		private ComListTable(String name, ActiveXComponent client) {
			Dispatch workbench = Dispatch.get(client, "Workbench").toDispatch();
			Dispatch lists = Dispatch.get(workbench, "Lists").toDispatch();
			this.dispatch = Dispatch.get(lists, name).toDispatch();
		}

		/** Number of active rows (excluding the header row). */
		@Override
		public int size() {
			return Dispatch.get(dispatch, "NumActiveElements").getInt();
		}

		@Override
		public String[][] attrs(String... names) {
			setColumns(names);

			SafeArray sa = Dispatch.call(dispatch, "SaveToArray").toSafeArray();
			int rows = size();
			String[][] out = new String[rows][names.length];

			for (int row = 0; row < rows; row++) {
				for (int c = 0; c < names.length; c++) {
					try {
						out[row][c] = sa.getString(row, c);
					} catch (Exception ex) {
						out[row][c] = "";
					}
				}
			}
			return out;
		}

		private void setColumns(String[] names) {
			for (String name : names) {
				int stringFormatType = name.toLowerCase().startsWith("time") ? 3 : 0; // time always as seconds
				try {
					Dispatch.call(dispatch, "AddColumn", name, stringFormatType);
				} catch (Exception ignored) { }
			}
		}
	}

	public void clearFilters() {
		Dispatch filters = Dispatch.get(client, "Filters").toDispatch();
		Dispatch.call(filters, "InitAll");
	}

}
