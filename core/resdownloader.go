// Package core provides core functionality for the Res Downloader Android app
package core

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/elazarl/goproxy"
	"github.com/matoous/go-nanoid/v2"
)

type Resource struct {
	ID        string            `json:"id"`
	URL       string            `json:"url"`
	Type      string            `json:"type"`
	Platform  string            `json:"platform"`
	Filename  string            `json:"filename"`
	Size      int64             `json:"size"`
	Timestamp int64             `json:"timestamp"`
	Headers   map[string]string `json:"headers,omitempty"`
}

type Proxy struct {
	port      int
	server    *http.Server
	proxy     *goproxy.ProxyHttpServer
	resources map[string]*Resource
	mu        sync.RWMutex
	cb        func(*Resource)
}

var defaultProxy *Proxy
var proxyOnce sync.Once

func NewProxy(port int) *Proxy {
	proxyOnce.Do(func() {
		defaultProxy = &Proxy{
			port:      port,
			proxy:     goproxy.NewProxyHttpServer(),
			resources: make(map[string]*Resource),
		}
		defaultProxy.setupHandlers()
	})
	defaultProxy.port = port
	return defaultProxy
}

func (p *Proxy) setupHandlers() {
	p.proxy.Verbose = false

	p.proxy.OnRequest().DoFunc(func(r *http.Request, ctx *goproxy.ProxyCtx) (*http.Request, *http.Response) {
		resource := p.processRequest(r)
		if resource != nil && p.cb != nil {
			p.cb(resource)
		}
		return r, nil
	})

	p.proxy.OnResponse().DoFunc(func(r *http.Response, ctx *goproxy.ProxyCtx) *http.Response {
		if r != nil && r.Request != nil {
			resource := p.processResponse(r)
			if resource != nil && p.cb != nil {
				p.cb(resource)
			}
		}
		return r
	})
}

func (p *Proxy) processRequest(r *http.Request) *Resource {
	url := r.URL.String()
	if !isMediaURL(url) {
		return nil
	}

	resource := &Resource{
		ID:        generateID(),
		URL:       url,
		Type:      detectType(url),
		Platform:  detectPlatform(r.Host),
		Filename:  extractFilename(r.URL.Path),
		Timestamp: time.Now().UnixMilli(),
		Headers:   extractHeaders(r.Header),
	}

	p.mu.Lock()
	p.resources[resource.ID] = resource
	p.mu.Unlock()

	return resource
}

func (p *Proxy) processResponse(r *http.Response) *Resource {
	if r == nil || r.Request == nil {
		return nil
	}

	contentType := r.Header.Get("Content-Type")
	if !isMediaContentType(contentType) {
		return nil
	}

	url := r.Request.URL.String()
	resource := &Resource{
		ID:        generateID(),
		URL:       url,
		Type:      detectTypeFromContentType(contentType),
		Platform:  detectPlatform(r.Request.Host),
		Filename:  extractFilename(r.Request.URL.Path),
		Size:      r.ContentLength,
		Timestamp: time.Now().UnixMilli(),
	}

	p.mu.Lock()
	p.resources[resource.ID] = resource
	p.mu.Unlock()

	return resource
}

func (p *Proxy) Start() error {
	p.server = &http.Server{
		Addr:    fmt.Sprintf(":%d", p.port),
		Handler: p.proxy,
	}
	return p.server.ListenAndServe()
}

func (p *Proxy) Stop() error {
	if p.server != nil {
		return p.server.Shutdown(context.Background())
	}
	return nil
}

func (p *Proxy) SetCallback(cb func(*Resource)) {
	p.cb = cb
}

func (p *Proxy) GetResources() []*Resource {
	p.mu.RLock()
	defer p.mu.RUnlock()

	result := make([]*Resource, 0, len(p.resources))
	for _, r := range p.resources {
		result = append(result, r)
	}
	return result
}

func (p *Proxy) Clear() {
	p.mu.Lock()
	p.resources = make(map[string]*Resource)
	p.mu.Unlock()
}

func (p *Proxy) Remove(id string) {
	p.mu.Lock()
	delete(p.resources, id)
	p.mu.Unlock()
}

func generateID() string {
	id, _ := gonanoid.New()
	return id
}

func isMediaURL(url string) bool {
	exts := []string{".mp4", ".m3u8", ".ts", ".flv", ".mp3", ".flac", ".wav", ".aac", ".ogg", ".m4a", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"}
	lower := strings.ToLower(url)
	for _, ext := range exts {
		if strings.Contains(lower, ext) {
			return true
		}
	}
	return false
}

func isMediaContentType(ct string) bool {
	mediaTypes := []string{"video/", "audio/", "image/", "application/x-mpegurl", "application/vnd.apple.mpegurl"}
	lower := strings.ToLower(ct)
	for _, mt := range mediaTypes {
		if strings.Contains(lower, mt) {
			return true
		}
	}
	return false
}

func detectType(url string) string {
	lower := strings.ToLower(url)
	switch {
	case strings.Contains(lower, ".m3u8"):
		return "m3u8"
	case strings.Contains(lower, ".mp4") || strings.Contains(lower, ".flv") || strings.Contains(lower, ".ts"):
		return "video"
	case strings.Contains(lower, ".mp3") || strings.Contains(lower, ".flac") || strings.Contains(lower, ".wav") || strings.Contains(lower, ".m4a"):
		return "audio"
	case strings.Contains(lower, ".jpg") || strings.Contains(lower, ".png") || strings.Contains(lower, ".gif") || strings.Contains(lower, ".webp"):
		return "image"
	default:
		return "other"
	}
}

func detectTypeFromContentType(ct string) string {
	lower := strings.ToLower(ct)
	switch {
	case strings.Contains(lower, "mpegurl"):
		return "m3u8"
	case strings.Contains(lower, "video"):
		return "video"
	case strings.Contains(lower, "audio"):
		return "audio"
	case strings.Contains(lower, "image"):
		return "image"
	default:
		return "other"
	}
}

func detectPlatform(host string) string {
	lower := strings.ToLower(host)
	switch {
	case strings.Contains(lower, "weixin.qq.com") || strings.Contains(lower, "wechat.com") || strings.Contains(lower, "wxtingyun.com"):
		return "wechat"
	case strings.Contains(lower, "douyin.com") || strings.Contains(lower, "iesdouyin.com") || strings.Contains(lower, "amemv.com"):
		return "douyin"
	case strings.Contains(lower, "kuaishou.com") || strings.Contains(lower, "kspkg.com"):
		return "kuaishou"
	case strings.Contains(lower, "xiaohongshu.com") || strings.Contains(lower, "xhslink.com"):
		return "xiaohongshu"
	case strings.Contains(lower, "kugou.com") || strings.Contains(lower, "kgimg.com"):
		return "kugou"
	case strings.Contains(lower, "y.qq.com") || strings.Contains(lower, "music.qq.com"):
		return "qqmusic"
	default:
		return "other"
	}
}

func extractFilename(path string) string {
	if path == "" {
		return "download"
	}
	parts := strings.Split(path, "/")
	filename := parts[len(parts)-1]
	if idx := strings.Index(filename, "?"); idx != -1 {
		filename = filename[:idx]
	}
	if filename == "" {
		return "download"
	}
	return filename
}

func extractHeaders(h http.Header) map[string]string {
	keys := []string{"User-Agent", "Referer", "Cookie", "Accept", "Range"}
	result := make(map[string]string)
	for _, k := range keys {
		if v := h.Get(k); v != "" {
			result[k] = v
		}
	}
	return result
}

type Downloader struct {
	client *http.Client
}

func NewDownloader() *Downloader {
	return &Downloader{
		client: &http.Client{
			Timeout: 30 * time.Minute,
		},
	}
}

func (d *Downloader) Download(url string, headers map[string]string) ([]byte, error) {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}

	resp, err := d.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusPartialContent {
		return nil, fmt.Errorf("status %d", resp.StatusCode)
	}

	return io.ReadAll(resp.Body)
}

type M3U8Info struct {
	Segments []M3U8Segment
	BaseURL  string
}

type M3U8Segment struct {
	URL   string
	Seq   int
}

func ParseM3U8(content string, baseURL string) (*M3U8Info, error) {
	info := &M3U8Info{
		Segments: make([]M3U8Segment, 0),
		BaseURL:  baseURL,
	}

	scanner := bufio.NewScanner(strings.NewReader(content))
	var seq int

	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#EXTM3U") || strings.HasPrefix(line, "#EXT-X-VERSION") {
			continue
		}
		if strings.HasPrefix(line, "#EXTINF:") {
			continue
		}
		if !strings.HasPrefix(line, "#") {
			segURL := line
			if !strings.HasPrefix(segURL, "http") {
				if strings.HasSuffix(info.BaseURL, "/") && strings.HasPrefix(segURL, "/") {
					segURL = info.BaseURL + segURL[1:]
				} else if !strings.HasSuffix(info.BaseURL, "/") && !strings.HasPrefix(segURL, "/") {
					segURL = info.BaseURL + "/" + segURL
				} else {
					segURL = info.BaseURL + segURL
				}
			}
			info.Segments = append(info.Segments, M3U8Segment{
				URL: segURL,
				Seq: seq,
			})
			seq++
		}
	}

	return info, scanner.Err()
}

type ReleaseInfo struct {
	TagName string `json:"tag_name"`
	Name    string `json:"name"`
	Body    string `json:"body"`
	URL     string `json:"html_url"`
	Assets  []AssetInfo `json:"assets"`
}

type AssetInfo struct {
	Name    string `json:"name"`
	URL     string `json:"browser_download_url"`
	Size    int64  `json:"size"`
}

func CheckLatestRelease(owner, repo string) (*ReleaseInfo, error) {
	url := fmt.Sprintf("https://api.github.com/repos/%s/%s/releases/latest", owner, repo)

	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/vnd.github.v3+json")

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}

	var release ReleaseInfo
	if err := json.NewDecoder(resp.Body).Decode(&release); err != nil {
		return nil, err
	}

	return &release, nil
}

func DownloadFile(url string, headers map[string]string) ([]byte, error) {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}

	client := &http.Client{Timeout: 60 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}

	var buf bytes.Buffer
	_, err = io.Copy(&buf, resp.Body)
	return buf.Bytes(), err
}
