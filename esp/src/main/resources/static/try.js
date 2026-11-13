const TryApi = {
  mount({ form, live, extra, operations }) {
    const select = form.querySelector('#operation');
    const fields = form.querySelector('#fields');
    let blobUrl = null;

    operations.forEach((operation, index) => {
      const option = document.createElement('option');
      option.value = String(index);
      option.textContent = operation.label;
      select.appendChild(option);
    });

    function current() {
      return operations[Number(select.value)];
    }

    function render() {
      const operation = current();
      fields.replaceChildren();
      (operation.fields || []).forEach((field) => {
        const label = document.createElement('label');
        label.append(field.label);
        let input;
        if (field.type === 'textarea') {
          input = document.createElement('textarea');
          input.rows = field.rows || 8;
        } else if (field.type === 'select') {
          input = document.createElement('select');
          field.options.forEach((value) => {
            const option = document.createElement('option');
            option.value = value;
            option.textContent = value;
            input.appendChild(option);
          });
        } else {
          input = document.createElement('input');
          input.type = field.type || 'text';
        }
        input.name = field.name;
        if (field.value != null && input.tagName !== 'SELECT') input.value = field.value;
        if (field.type === 'select' && field.value) input.value = field.value;
        if (field.placeholder) input.placeholder = field.placeholder;
        label.appendChild(input);
        fields.appendChild(label);
      });
      if (operation.note) {
        const note = document.createElement('p');
        note.className = 'hint';
        note.textContent = operation.note;
        fields.appendChild(note);
      }
    }

    function read(name) {
      const element = form.elements[name];
      return element ? String(element.value) : '';
    }

    select.addEventListener('change', render);

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const operation = current();
      const button = form.querySelector('button[type="submit"]');
      let path = operation.path;
      const missing = [];
      path = path.replace(/\{([^}]+)\}/g, (_, name) => {
        const value = read(name).trim();
        if (!value) missing.push(name);
        return encodeURIComponent(value);
      });
      if (missing.length) {
        live.textContent = 'Enter ' + missing.join(', ') + '.';
        extra.replaceChildren();
        return;
      }
      const query = new URLSearchParams();
      (operation.fields || []).filter((field) => field.in === 'query').forEach((field) => {
        const value = read(field.name).trim();
        if (value) query.set(field.name, value);
      });
      const headers = {};
      (operation.fields || []).filter((field) => field.in === 'header').forEach((field) => {
        const value = read(field.name).trim();
        if (value) headers[field.name] = value;
      });
      const options = { method: operation.method || 'GET', headers };
      try {
      if (operation.body === 'multipart') {
        const body = new FormData();
        (operation.fields || []).forEach((field) => {
          if (field.type === 'file') {
            const file = form.elements[field.name].files[0];
            if (file) body.append(field.name, file);
          } else if (field.in !== 'path' && field.in !== 'query' && field.in !== 'header') {
            body.append(field.name, read(field.name));
          }
        });
        options.body = body;
      } else if (operation.body === 'json') {
        headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(operation.build(read, form));
      } else if (operation.body === 'text') {
        headers['Content-Type'] = operation.contentType;
        options.body = read('body');
      } else if (operation.body === 'form') {
        const body = new URLSearchParams();
        (operation.fields || []).forEach((field) => {
          if (field.in === 'path' || field.in === 'query' || field.in === 'header') return;
          const value = read(field.name);
          if (field.repeat) {
            value.split(',').map((item) => item.trim()).filter(Boolean).forEach((item) => body.append(field.name, item));
          } else if (value !== '') {
            body.append(field.name, value);
          }
        });
        headers['Content-Type'] = 'application/x-www-form-urlencoded';
        options.body = body;
      }
      } catch (error) {
        live.textContent = error.message;
        extra.replaceChildren();
        return;
      }
      const url = path + (query.toString() ? '?' + query.toString() : '');
      button.disabled = true;
      live.textContent = (options.method || 'GET') + ' ' + url + ' …';
      extra.replaceChildren();
      if (blobUrl) {
        URL.revokeObjectURL(blobUrl);
        blobUrl = null;
      }
      try {
        const response = await fetch(url, options);
        const type = response.headers.get('content-type') || '';
        if (type.includes('png') || type.includes('jpeg') || type.includes('pdf') || type.includes('octet-stream')) {
          const blob = await response.blob();
          blobUrl = URL.createObjectURL(blob);
          live.textContent = response.status + ' ' + (type || 'binary') + ' (' + blob.size + ' bytes)';
          if (type.includes('png') || type.includes('jpeg')) {
            const image = document.createElement('img');
            image.src = blobUrl;
            image.alt = 'Response image';
            extra.appendChild(image);
          } else {
            const link = document.createElement('a');
            link.href = blobUrl;
            link.target = '_blank';
            link.rel = 'noopener';
            link.textContent = 'Open response';
            extra.appendChild(link);
          }
        } else {
          let text = await response.text();
          if (text.length > 5000) text = text.slice(0, 5000) + '\n…';
          live.textContent = response.status + '\n' + text;
        }
      } catch (error) {
        live.textContent = error.message;
      } finally {
        button.disabled = false;
      }
    });

    render();
  }
};
