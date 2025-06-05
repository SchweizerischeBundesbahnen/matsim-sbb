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

	// ------------------------------------------------------------------
	// Lifecycle
	// ------------------------------------------------------------------

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

	// ------------------------------------------------------------------
	// Public API – high‑level table access only
	// ------------------------------------------------------------------

	/** Fluent wrapper giving safe, typed access to any Visum table. */
	@Override
    public Table getTable(String name) {
		Objects.requireNonNull(name, "table name");
		Dispatch net = Dispatch.get(client, "Net").toDispatch();
		Dispatch tbl = Dispatch.get(net, name).toDispatch();
		return new Table(tbl);
	}

	// ------------------------------------------------------------------
	// Inner helper representing one table (Nodes, Links, StopPoints ...)
	// ------------------------------------------------------------------

	public static final class Table implements DataTable {
		private final Dispatch dispatch;

		private Table(Dispatch dispatch) {
			this.dispatch = dispatch;
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

		// Convenience helpers ------------------------------------------------

		/** Single column as typed list (Double/Integer/String) – optional sugar. */
		public <T> List<T> column(String name, Class<T> type) {
			String[][] a = attrs(name);
			List<T> res = new ArrayList<>(a.length);
			for (String[] row : a) {
				res.add(convert(row[0], type));
			}
			return res;
		}

		@SuppressWarnings("unchecked")
		private static <T> T convert(String v, Class<T> type) {
			if (v == null || v.isEmpty()) return null;
			if (type == Integer.class) return (T) (Integer) (int) Double.parseDouble(v);
			if (type == Double.class)  return (T) (Double) Double.valueOf(v);
			// fallback – return string
			return (T) v;
		}
	}

	public void clearFilters() {
		Dispatch filters = Dispatch.get(client, "Filters").toDispatch();
		Dispatch.call(filters, "InitAll");
	}

}
