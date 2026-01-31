import { createApp, defineComponent, ref, computed, nextTick, onMounted } from 'vue';
import Prism from 'prismjs';
import 'prismjs/components/prism-clike';
import 'prismjs/components/prism-java';

interface FileData {
  path: string;
  counts: Record<string, number>;
  content: string;
  project?: string;
}

interface ReportPayload {
  generatedAt: number;
  files: FileData[];
}

interface TreeNode {
  name: string;
  path: string;
  depth: number;
  children: TreeNode[] | null;
  lines: string[];
  counts: Record<string, number>;
  totalCount: number;
  formattedTotal: string;
  flash: boolean;
  filePath?: string;
  project?: string;
}

declare global {
  interface Window {
    REPORT_DATA?: ReportPayload | FileData[];
    REPORT_GENERATED_AT?: number;
    REPORT_JSON?: string;
    REPORT_JSONP?: string;
    loadExecutionData?: (data?: unknown, generatedAt?: number) => void;
  }
}

const normalizePayload = (payload: ReportPayload | FileData[] | undefined): ReportPayload => {
  if (!payload) {
    return { generatedAt: 0, files: [] };
  }
  if (Array.isArray(payload)) {
    return { generatedAt: 0, files: payload };
  }
  if (Array.isArray(payload.files)) {
    return { generatedAt: payload.generatedAt || 0, files: payload.files };
  }
  return { generatedAt: 0, files: [] };
};

const initialPayload = normalizePayload(window.REPORT_DATA);
const jsonFile = window.REPORT_JSON || 'execution-report.json';
const jsonpFile = window.REPORT_JSONP || 'execution-report.js';
const POLL_INTERVAL = 2000;
const OFFLINE_TIMEOUT = Math.max(6000, POLL_INTERVAL * 3);
const pageLoadedAt = Date.now();
let lastUpdate =
  Math.max(initialPayload.generatedAt, window.REPORT_GENERATED_AT || 0, pageLoadedAt);

const buildTree = (files: FileData[]): TreeNode[] => {
  const root: TreeNode[] = [];
  const sortedFiles = [...files].sort((a, b) => {
    const projectA = (a.project || 'unknown').toLowerCase();
    const projectB = (b.project || 'unknown').toLowerCase();
    const projectCompare = projectA.localeCompare(projectB);
    if (projectCompare !== 0) {
      return projectCompare;
    }
    return a.path.localeCompare(b.path);
  });

  sortedFiles.forEach((file) => {
    const project = (file.project || 'unknown').trim() || 'unknown';
    const parts = file.path.split('/');
    const segments = [project, ...parts];
    let currentLevel = root;
    let currentPath = '';

    segments.forEach((segment, index) => {
      currentPath += (currentPath ? '/' : '') + segment;
      let node = currentLevel.find((n) => n.name === segment && n.depth === index);
      if (!node) {
        node = {
          name: segment,
          path: currentPath,
          depth: index,
          children: [],
          lines: [],
          counts: {},
          totalCount: 0,
          formattedTotal: '',
          flash: false
        };
        currentLevel.push(node);
      }

      if (index === segments.length - 1) {
        const oldTotal = node.totalCount || 0;
        node.children = null;
        node.filePath = file.path;
        node.project = project;
        node.lines = file.content.split(/\r?\n/);
        node.counts = file.counts;
        const sum = Object.values(file.counts).reduce((a, b) => a + b, 0);
        node.totalCount = sum;
        node.formattedTotal = formatCount(sum);
        if (sum > oldTotal) {
          node.flash = true;
          setTimeout(() => (node.flash = false), 1000);
        }
      } else if (node.children) {
        node.project = project;
        currentLevel = node.children;
      }
    });
  });

  return root;
};

const updateTreeData = (nodes: TreeNode[], newFiles: FileData[]) => {
  const fileMap = new Map(
    newFiles.map((f) => {
      const project = (f.project || 'unknown').trim() || 'unknown';
      return [`${project}::${f.path}`, f];
    })
  );
  const traverse = (nodeList: TreeNode[]) => {
    for (const node of nodeList) {
      if (node.children) {
        traverse(node.children);
      } else {
        const project = node.project || 'unknown';
        const lookupPath = node.filePath || node.path;
        const key = lookupPath ? `${project}::${lookupPath}` : undefined;
        const newData = key ? fileMap.get(key) : undefined;
        if (newData) {
          const oldTotal = node.totalCount || 0;
          node.counts = newData.counts;
          const newSum = Object.values(newData.counts).reduce((a, b) => a + b, 0);
          node.totalCount = newSum;
          node.formattedTotal = formatCount(newSum);
          if (newSum > oldTotal) {
            node.flash = true;
            setTimeout(() => (node.flash = false), 1000);
          }
        }
      }
    }
  };
  traverse(nodes);
};

const formatCount = (count: number) => {
  if (count >= 1000000) return (count / 1000000).toFixed(2) + 'M';
  if (count >= 10000) return (count / 1000).toFixed(1) + 'k';
  return count.toString();
};

const formatBigCount = (count: number) => new Intl.NumberFormat().format(count);

const calculateHeatmapColor = (count: number, max: number) => {
  if (!count) return 'transparent';
  const logCount = Math.log1p(count);
  const logMax = Math.log1p(max || 1);
  const ratio = logCount / logMax;
  const hue = 120 * (1 - ratio);
  return `hsla(${hue}, 85%, 45%, 0.85)`;
};

const setPrismTheme = (dark: boolean) => {
  const link = document.getElementById('prism-css') as HTMLLinkElement | null;
  if (!link) return;
  link.href = dark
    ? 'https://cdn.jsdelivr.net/npm/prismjs@1.29.0/themes/prism-okaidia.min.css'
    : 'https://cdn.jsdelivr.net/npm/prismjs@1.29.0/themes/prism-coy.min.css';
};

const initTheme = () => {
  const saved = localStorage.getItem('theme');
  const body = document.body;
  if (saved === 'light') {
    body.classList.add('light-mode');
    setPrismTheme(false);
  } else {
    body.classList.remove('light-mode');
    setPrismTheme(true);
  }
};

const TreeNode = defineComponent({
  name: 'TreeNode',
  props: {
    node: { type: Object as () => TreeNode, required: true },
    selectedPath: { type: String, required: true }
  },
  emits: ['select'],
  setup(props, { emit }) {
    const isOpen = ref(true);
    const isFolder = computed(() => !!props.node.children && props.node.children.length > 0);
    const toggle = () => {
      if (isFolder.value) isOpen.value = !isOpen.value;
      else emit('select', props.node);
    };
    return { isOpen, isFolder, toggle };
  },
  template: `
    <div>
      <div
        class="tree-item"
        :class="{ active: node.path === selectedPath, flash: node.flash }"
        @click="toggle"
        :style="{ paddingLeft: node.depth * 12 + 'px' }">
        <span class="icon">{{ isFolder ? (isOpen ? '📂' : '📁') : '📄' }}</span>
        <span style="flex:1; overflow:hidden; text-overflow:ellipsis;">{{ node.name }}</span>
        <span v-if="!isFolder && node.totalCount > 0"
              style="font-size:10px; color:var(--gutter-text); margin-left:6px;">
          {{ node.formattedTotal }}
        </span>
      </div>
      <div v-if="isFolder && isOpen" class="tree-group">
        <tree-node
          v-for="child in node.children"
          :key="child.path"
          :node="child"
          :selected-path="selectedPath"
          @select="$emit('select', $event)">
        </tree-node>
      </div>
    </div>
  `
});

const root = document.getElementById('app');
const appTemplate = root ? root.innerHTML : '';

createApp({
  components: { TreeNode },
  template: appTemplate,
  setup() {
    const rawData = ref(initialPayload.files);
    const fileTree = ref<TreeNode[]>(buildTree(rawData.value));
    const selectedFile = ref<TreeNode | null>(null);
    const highlightedCode = ref('');
    const isDarkMode = ref(true);
    const isLive = ref(false);
    const liveError = ref<string | null>(null);
    const globalMax = computed(() => {
      let max = 1;
      rawData.value.forEach((f) => {
        Object.values(f.counts).forEach((cnt) => {
          if (cnt > max) max = cnt;
        });
      });
      return max;
    });
    const totalFiles = computed(() => rawData.value.length);
    const totalExecutions = computed(() =>
      rawData.value.reduce((sum, file) => sum + Object.values(file.counts).reduce((a, b) => a + b, 0), 0)
    );

    initTheme();

    const toggleTheme = () => {
      isDarkMode.value = !isDarkMode.value;
      document.body.classList.toggle('light-mode', !isDarkMode.value);
      localStorage.setItem('theme', isDarkMode.value ? 'dark' : 'light');
      setPrismTheme(isDarkMode.value);
    };

    const selectFile = (node: TreeNode) => {
      if (selectedFile.value === node) return;
      selectedFile.value = node;
      nextTick(() => {
        highlightCode();
        if (node && node.path) {
          window.history.replaceState(null, '', '#' + encodeURIComponent(node.path));
        }
      });
    };

    const highlightCode = () => {
      if (!selectedFile.value) return;
      const raw = selectedFile.value.lines.join('\n');
      highlightedCode.value = Prism.highlight(raw, Prism.languages.java, 'java');
    };

    const updateLiveStatus = () => {
      if (!lastUpdate) {
        isLive.value = false;
        liveError.value = 'Offline';
        return;
      }
      const age = Date.now() - lastUpdate;
      if (age > OFFLINE_TIMEOUT) {
        isLive.value = false;
        liveError.value = 'Stale';
      } else {
        isLive.value = true;
        liveError.value = null;
      }
    };

    const loadExecutionData = (newData?: unknown, generatedAt?: number) => {
      if (!newData) return;
      const payload = normalizePayload(newData as ReportPayload);
      const incomingAt = generatedAt || payload.generatedAt || 0;
      if (incomingAt && incomingAt <= lastUpdate) return;
      if (!incomingAt && lastUpdate > 0) return;
      rawData.value = payload.files;
      updateTreeData(fileTree.value, payload.files);
      isLive.value = true;
      liveError.value = null;
      lastUpdate = incomingAt || Date.now();
    };

    window.loadExecutionData = loadExecutionData;

    const tryJSONP = (cacheBust = true) =>
      new Promise<boolean>((resolve) => {
        const oldScript = document.getElementById('jsonp-data');
        if (oldScript) oldScript.remove();
        const script = document.createElement('script');
        script.id = 'jsonp-data';
        const isLocal = window.location.protocol === 'file:';
        const useBust = cacheBust || isLocal;
        const url = jsonpFile + (useBust ? '?t=' + Date.now() : '');
        let resolved = false;
        const finish = (ok: boolean) => {
          if (resolved) return;
          resolved = true;
          resolve(ok);
        };
        script.onload = () => finish(true);
        script.onerror = () => finish(false);
        setTimeout(() => finish(false), 1500);
        script.src = url;
        document.head.appendChild(script);
      });

    const tryFetch = async () => {
      try {
        const res = await fetch(jsonFile + '?t=' + Date.now());
        if (!res.ok) throw new Error('fetch failed');
        const data = await res.json();
        loadExecutionData(data);
        return true;
      } catch (error) {
        isLive.value = false;
        liveError.value = 'Offline';
        return false;
      }
    };

    const isPolling = ref(false);

    const pollData = async () => {
      if (isPolling.value) return;
      isPolling.value = true;
      const beforeUpdate = lastUpdate;
      let success = await tryJSONP(true);
      if (success && lastUpdate > beforeUpdate) {
        // Updated via JSONP
      } else if (window.location.protocol === 'file:') {
        success = await tryJSONP(false);
      }
      if (!success && window.location.protocol !== 'file:') {
        success = await tryFetch();
      }
      isPolling.value = false;
      if (!success) {
        isLive.value = false;
        liveError.value = 'Offline';
      }
    };

    setInterval(pollData, POLL_INTERVAL);
    setInterval(updateLiveStatus, 1000);
    updateLiveStatus();

    onMounted(() => {
      const hash = window.location.hash.substring(1);
      if (hash) {
        const path = decodeURIComponent(hash);
        const findNode = (nodes: TreeNode[]): TreeNode | null => {
          for (const node of nodes) {
            if (node.path === path) return node;
            if (node.children) {
              const found = findNode(node.children);
              if (found) return found;
            }
          }
          return null;
        };
        const target = findNode(fileTree.value);
        if (target) selectFile(target);
      }
    });

    const getExecutionCount = (lineNum: number) =>
      Number(selectedFile.value?.counts?.[lineNum.toString()] || 0);

    const getHeatmapColor = (count: number) => {
      return calculateHeatmapColor(count, globalMax.value);
    };

    return {
      fileTree,
      selectedFile,
      highlightedCode,
      totalFiles,
      totalExecutions,
      globalMax,
      selectFile,
      getExecutionCount,
      getHeatmapColor,
      formatCount,
      formatBigCount,
      toggleTheme,
      isDarkMode,
      isLive,
      liveError
    };
  }
}).mount('#app');
